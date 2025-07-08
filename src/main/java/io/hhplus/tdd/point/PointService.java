package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import org.springframework.stereotype.Service;

@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    /**
     * 사용자의 포인트를 조회합니다.
     * 
     * @param userId 사용자 ID
     * @return 사용자의 포인트 정보
     */
    public UserPoint getPoint(long userId) {
        return userPointTable.selectById(userId);
    }
}