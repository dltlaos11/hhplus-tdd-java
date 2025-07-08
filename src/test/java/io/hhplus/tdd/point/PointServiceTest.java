package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @InjectMocks
    private PointService pointService; // 아직 존재하지 않는 클래스

    @Test
    @DisplayName("사용자 ID로 포인트를 조회할 수 있다")
    void 포인트_조회_성공() {
        // given - 테스트를 위한 데이터 준비
        long userId = 1L;
        UserPoint expectedPoint = new UserPoint(userId, 1000L, System.currentTimeMillis());
        given(userPointTable.selectById(userId)).willReturn(expectedPoint);

        // when - 실제 테스트 대상 실행
        UserPoint result = pointService.getPoint(userId);
 
        // then - 결과 검증
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 포인트 조회 시 0 포인트를 반환한다")
    void 존재하지_않는_사용자_포인트_조회() {
        // given
        long userId = 999L;
        UserPoint emptyPoint = UserPoint.empty(userId); // 0 포인트로 초기화
        given(userPointTable.selectById(userId)).willReturn(emptyPoint);

        // when
        UserPoint result = pointService.getPoint(userId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(0L);
    }
}