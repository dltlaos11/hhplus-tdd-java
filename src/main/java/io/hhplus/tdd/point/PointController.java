package io.hhplus.tdd.point;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/point")
public class PointController {

    private static final Logger log = LoggerFactory.getLogger(PointController.class);
    
    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    /**
     * 특정 유저의 포인트를 조회하는 기능을 작성해주세요.
     */
    @GetMapping("{id}")
    public UserPoint point(@PathVariable long id) {
        log.info("포인트 조회 요청: userId={}", id);
        return pointService.getPoint(id);
    }

    /**
     * 특정 유저의 포인트 충전/이용 내역을 조회하는 기능입니다.
     * 
     * @param id 사용자 ID
     * @return 사용자의 포인트 내역 리스트 (충전/사용 모두 포함)
     */
    @GetMapping("{id}/histories")
    public List<PointHistory> history(@PathVariable long id) {
        log.info("포인트 내역 조회 요청: userId={}", id);
        return pointService.getHistory(id);
    }

    /**
     * 특정 유저의 포인트를 충전하는 기능입니다.
     * 
     * @param id 사용자 ID
     * @param amount 충전할 금액 (RequestBody로 전달)
     * @return 충전 후 사용자 포인트 정보
     */
    @PatchMapping("{id}/charge")
    public UserPoint charge(@PathVariable long id, @RequestBody long amount) {
        log.info("포인트 충전 요청: userId={}, amount={}", id, amount);
        return pointService.charge(id, amount);
    }

    /**
     * 특정 유저의 포인트를 사용하는 기능입니다.
     * 
     * @param id 사용자 ID
     * @param amount 사용할 금액 (RequestBody로 전달)
     * @return 사용 후 사용자 포인트 정보
     */
    @PatchMapping("{id}/use")
    public UserPoint use(@PathVariable long id, @RequestBody long amount) {
        log.info("포인트 사용 요청: userId={}, amount={}", id, amount);
        return pointService.use(id, amount);
    }
}