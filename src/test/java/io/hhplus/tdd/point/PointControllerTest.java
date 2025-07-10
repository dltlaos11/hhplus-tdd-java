package io.hhplus.tdd.point;

import io.hhplus.tdd.point.exception.InvalidAmountException;
import io.hhplus.tdd.point.exception.ExceedsMaxPointException;
import io.hhplus.tdd.point.exception.InsufficientPointException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;

/**
 * PointController 통합 테스트
 * 
 * @WebMvcTest 사용 이유:
 * 1. 웹 계층만 슬라이스 테스트 - 빠른 실행
 * 2. Spring MVC 설정 자동 구성
 * 3. MockMvc를 통한 HTTP 요청/응답 테스트
 * 4. 실제 네트워크 호출 없이 웹 계층 검증
 */
@WebMvcTest(PointController.class)
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PointService pointService;

    // =============== 기존 조회 테스트 ===============

    @Test
    @DisplayName("GET /point/{id} 요청으로 포인트를 조회할 수 있다")
    void 포인트_조회_API_테스트() throws Exception {
        // given
        long userId = 1L;
        UserPoint userPoint = new UserPoint(userId, 5000L, System.currentTimeMillis());
        given(pointService.getPoint(userId)).willReturn(userPoint);

        // when & then
        mockMvc.perform(get("/point/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(5000));
    }
    // =============== 새로운 사용 API 테스트 ===============

    @Test
    @DisplayName("PATCH /point/{id}/use 요청으로 포인트를 사용할 수 있다")
    void 포인트_사용_API_성공_테스트() throws Exception {
        // given
        long userId = 1L;
        long useAmount = 1000L;
        UserPoint usedPoint = new UserPoint(userId, 4000L, System.currentTimeMillis());
        
        given(pointService.use(userId, useAmount)).willReturn(usedPoint);

        // when & then
        mockMvc.perform(patch("/point/{id}/use", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(useAmount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(4000));
    }

    @Test
    @DisplayName("유효하지 않은 사용 금액으로 요청 시 400 에러를 반환한다")
    void 유효하지_않은_사용_금액_API_실패_테스트() throws Exception {
        // given
        long userId = 1L;
        long invalidAmount = 0L;
        
        willThrow(new InvalidAmountException("사용 금액은 0보다 커야 합니다"))
                .given(pointService).use(userId, invalidAmount);

        // when & then
        mockMvc.perform(patch("/point/{id}/use", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(invalidAmount)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("포인트 부족 시 400 에러를 반환한다")
    void 포인트_부족_사용_API_실패_테스트() throws Exception {
        // given
        long userId = 1L;
        long excessiveAmount = 6000L;
        
        willThrow(new InsufficientPointException(excessiveAmount, 5000L))
                .given(pointService).use(userId, excessiveAmount);

        // when & then
        mockMvc.perform(patch("/point/{id}/use", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(excessiveAmount)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("전체 포인트 사용 시 0원이 된다")
    void 전체_포인트_사용_API_성공_테스트() throws Exception {
        // given
        long userId = 2L;
        long useAmount = 5000L;
        UserPoint emptyPoint = new UserPoint(userId, 0L, System.currentTimeMillis());
        
        given(pointService.use(userId, useAmount)).willReturn(emptyPoint);

        // when & then
        mockMvc.perform(patch("/point/{id}/use", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(useAmount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(0));
    }

    // =============== 새로운 내역 조회 API 테스트 ===============

    @Test
    @DisplayName("GET /point/{id}/histories 요청으로 포인트 내역을 조회할 수 있다")
    void 포인트_내역_조회_API_성공_테스트() throws Exception {
        // given
        long userId = 1L;
        List<PointHistory> histories = List.of(
                new PointHistory(1L, userId, 1000L, TransactionType.CHARGE, System.currentTimeMillis()),
                new PointHistory(2L, userId, 500L, TransactionType.USE, System.currentTimeMillis())
        );
        
        given(pointService.getHistory(userId)).willReturn(histories);

        // when & then
        mockMvc.perform(get("/point/{id}/histories", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(userId))
                .andExpect(jsonPath("$[0].amount").value(1000))
                .andExpect(jsonPath("$[0].type").value("CHARGE"))
                .andExpect(jsonPath("$[1].userId").value(userId))
                .andExpect(jsonPath("$[1].amount").value(500))
                .andExpect(jsonPath("$[1].type").value("USE"));
    }

    @Test
    @DisplayName("포인트 내역이 없는 사용자의 경우 빈 배열을 반환한다")
    void 포인트_내역_없는_사용자_API_테스트() throws Exception {
        // given
        long userId = 999L;
        List<PointHistory> emptyHistories = List.of();
        
        given(pointService.getHistory(userId)).willReturn(emptyHistories);

        // when & then
        mockMvc.perform(get("/point/{id}/histories", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("충전과 사용이 섞인 내역을 조회할 수 있다")
    void 혼합_내역_조회_API_테스트() throws Exception {
        // given
        long userId = 2L;
        List<PointHistory> mixedHistories = List.of(
                new PointHistory(1L, userId, 5000L, TransactionType.CHARGE, System.currentTimeMillis()),
                new PointHistory(2L, userId, 2000L, TransactionType.USE, System.currentTimeMillis()),
                new PointHistory(3L, userId, 3000L, TransactionType.CHARGE, System.currentTimeMillis())
        );
        
        given(pointService.getHistory(userId)).willReturn(mixedHistories);

        // when & then
        mockMvc.perform(get("/point/{id}/histories", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].type").value("CHARGE"))
                .andExpect(jsonPath("$[0].amount").value(5000))
                .andExpect(jsonPath("$[1].type").value("USE"))
                .andExpect(jsonPath("$[1].amount").value(2000))
                .andExpect(jsonPath("$[2].type").value("CHARGE"))
                .andExpect(jsonPath("$[2].amount").value(3000));
    }

    @Test
    @DisplayName("존재하지 않는 사용자 조회 시 0 포인트를 반환한다")
    void 존재하지_않는_사용자_포인트_조회_API_테스트() throws Exception {
        // given
        long userId = 999L;
        UserPoint emptyPoint = UserPoint.empty(userId);
        given(pointService.getPoint(userId)).willReturn(emptyPoint);

        // when & then
        mockMvc.perform(get("/point/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(0));
    }

    // =============== 새로운 충전 API 테스트 ===============

    @Test
    @DisplayName("PATCH /point/{id}/charge 요청으로 포인트를 충전할 수 있다")
    void 포인트_충전_API_성공_테스트() throws Exception {
        // given
        long userId = 1L;
        long chargeAmount = 1000L;
        UserPoint chargedPoint = new UserPoint(userId, 6000L, System.currentTimeMillis());
        
        given(pointService.charge(userId, chargeAmount)).willReturn(chargedPoint);

        // when & then
        mockMvc.perform(patch("/point/{id}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(chargeAmount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(6000));
    }

    @Test
    @DisplayName("유효하지 않은 충전 금액으로 요청 시 400 에러를 반환한다")
    void 유효하지_않은_충전_금액_API_실패_테스트() throws Exception {
        // given
        long userId = 1L;
        long invalidAmount = 0L;
        
        willThrow(new InvalidAmountException("충전 금액은 0보다 커야 합니다"))
                .given(pointService).charge(userId, invalidAmount);

        // when & then
        mockMvc.perform(patch("/point/{id}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(invalidAmount)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("최대 포인트 초과 충전 시 400 에러를 반환한다")
    void 최대_포인트_초과_충전_API_실패_테스트() throws Exception {
        // given
        long userId = 1L;
        long excessiveAmount = 200000L;
        
        willThrow(new ExceedsMaxPointException("최대 보유 가능 포인트는 1,000,000원입니다"))
                .given(pointService).charge(userId, excessiveAmount);

        // when & then
        mockMvc.perform(patch("/point/{id}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(excessiveAmount)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("첫 충전 시 0 포인트에서 충전 금액만큼 증가한다")
    void 첫_충전_API_성공_테스트() throws Exception {
        // given
        long userId = 2L;
        long chargeAmount = 1000L;
        UserPoint chargedPoint = new UserPoint(userId, chargeAmount, System.currentTimeMillis());
        
        given(pointService.charge(userId, chargeAmount)).willReturn(chargedPoint);

        // when & then
        mockMvc.perform(patch("/point/{id}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(chargeAmount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(chargeAmount));
    }
}