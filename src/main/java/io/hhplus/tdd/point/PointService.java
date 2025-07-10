package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.point.policy.ChargePolicy;
import io.hhplus.tdd.point.policy.UsePolicy;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 포인트 관련 비즈니스 로직을 담당하는 서비스 클래스
 * 
 * 설계 원칙:
 * 1. 단일 책임 원칙: 포인트 관련 비즈니스 로직만 담당
 * 2. 의존성 주입: 생성자 주입으로 테스트 용이성 확보
 * 3. 정책 분리: 비즈니스 규칙을 별도 Policy 클래스로 위임
 * 4. 트랜잭션 관점: 포인트 변경과 히스토리 기록을 원자적으로 처리
 */
@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final ChargePolicy chargePolicy;
    private final UsePolicy usePolicy;

    /**
     * Spring 의존성 주입을 위한 생성자
     * 
     * @param userPointTable 사용자 포인트 데이터 접근 객체
     * @param pointHistoryTable 포인트 히스토리 데이터 접근 객체  
     * @param chargePolicy 충전 정책 검증 객체
     * @param usePolicy 사용 정책 검증 객체
     */
    public PointService(UserPointTable userPointTable, 
                       PointHistoryTable pointHistoryTable,
                       ChargePolicy chargePolicy,
                       UsePolicy usePolicy) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
        this.chargePolicy = chargePolicy;
        this.usePolicy = usePolicy;
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

    /**
     * 사용자의 포인트를 충전합니다.
     * 
     * 처리 과정:
     * 1. 현재 포인트 조회
     * 2. 충전 정책 검증 (금액 유효성, 최대 한도 등)
     * 3. 포인트 업데이트
     * 4. 충전 히스토리 기록
     * 
     * @param userId 사용자 ID
     * @param amount 충전할 금액
     * @return 충전 후 사용자 포인트 정보
     * @throws io.hhplus.tdd.point.exception.InvalidAmountException 유효하지 않은 충전 금액
     * @throws io.hhplus.tdd.point.exception.ExceedsMaxPointException 최대 포인트 초과
     */
    public UserPoint charge(long userId, long amount) {
        // 1. 현재 포인트 조회
        UserPoint currentUserPoint = userPointTable.selectById(userId);
        
        // 2. 충전 정책 검증
        chargePolicy.validate(amount, currentUserPoint.point());
        
        // 3. 새로운 포인트 계산 및 업데이트
        long newPoint = currentUserPoint.point() + amount;
        UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, newPoint);
        
        // 4. 충전 히스토리 기록
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());
        
        return updatedUserPoint;
    }

    /**
     * 사용자의 포인트 사용/충전 내역을 조회합니다.
     * 
     * 처리 과정:
     * 1. 사용자 ID로 모든 포인트 히스토리 조회
     * 2. 충전/사용 내역을 시간순으로 반환
     * 
     * @param userId 사용자 ID
     * @return 사용자의 포인트 내역 리스트 (충전/사용 모두 포함)
     */
    public List<PointHistory> getHistory(long userId) {
        return pointHistoryTable.selectAllByUserId(userId);
    }

    /**
     * 사용자의 포인트를 사용합니다.
     * 
     * 처리 과정:
     * 1. 현재 포인트 조회
     * 2. 사용 정책 검증 (금액 유효성, 잔고 충분성 등)
     * 3. 포인트 차감 및 업데이트
     * 4. 사용 히스토리 기록
     * 
     * @param userId 사용자 ID
     * @param amount 사용할 금액
     * @return 사용 후 사용자 포인트 정보
     * @throws io.hhplus.tdd.point.exception.InvalidAmountException 유효하지 않은 사용 금액
     * @throws io.hhplus.tdd.point.exception.InsufficientPointException 포인트 부족
     */
    public UserPoint use(long userId, long amount) {
        // 1. 현재 포인트 조회
        UserPoint currentUserPoint = userPointTable.selectById(userId);
        
        // 2. 사용 정책 검증
        usePolicy.validate(amount, currentUserPoint.point());
        
        // 3. 새로운 포인트 계산 및 업데이트 (차감)
        long newPoint = currentUserPoint.point() - amount;
        UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, newPoint);
        
        // 4. 사용 히스토리 기록
        pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
        
        return updatedUserPoint;
    }
}