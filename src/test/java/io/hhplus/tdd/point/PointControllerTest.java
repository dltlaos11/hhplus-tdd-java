package io.hhplus.tdd.point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PointController.class)
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PointService pointService;

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
}