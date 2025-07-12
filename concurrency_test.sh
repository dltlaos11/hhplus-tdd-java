echo "🔄 동시성 테스트 시작"
echo "===================="

USER_ID=1001
BASE_URL="http://localhost:8080"

# 초기 상태 설정
echo "1. 초기 포인트 설정 (1000원)"
curl -s -X PATCH \
  -H "Content-Type: application/json" \
  -d "1000" \
  $BASE_URL/point/$USER_ID/charge > /dev/null

echo "초기 포인트 확인:"
curl -s $BASE_URL/point/$USER_ID | jq

# 동시 충전 테스트
echo -e "\n2. 동시에 10번 충전 (각 100원) 실행..."

# 백그라운드로 동시 실행
for i in {1..10}; do
  {
    echo "요청 $i 시작 ($(date +%H:%M:%S.%3N))"
    RESPONSE=$(curl -s -X PATCH \
      -H "Content-Type: application/json" \
      -d "100" \
      $BASE_URL/point/$USER_ID/charge)
    echo "요청 $i 완료: $(echo $RESPONSE | jq -r '.point')원 ($(date +%H:%M:%S.%3N))"
  } &
done

# 모든 백그라운드 작업 완료 대기
wait

echo -e "\n3. 최종 결과 확인"
FINAL_POINT=$(curl -s $BASE_URL/point/$USER_ID | jq -r '.point')
HISTORY_COUNT=$(curl -s $BASE_URL/point/$USER_ID/histories | jq 'length')

echo "=== 동시성 테스트 결과 ==="
echo "기대 포인트: 2000원 (1000 + 10×100)"
echo "실제 포인트: ${FINAL_POINT}원"
echo "히스토리 개수: ${HISTORY_COUNT}개 (11개 예상)"

if [ "$FINAL_POINT" -eq 2000 ] && [ "$HISTORY_COUNT" -eq 11 ]; then
    echo "✅ 동시성 테스트 성공! Lost Update 없음"
else
    echo "❌ 동시성 문제 발생!"
fi
