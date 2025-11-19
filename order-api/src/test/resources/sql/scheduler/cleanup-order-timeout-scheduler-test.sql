-- OrderTimeoutScheduler 통합 테스트 데이터 정리

-- 주문 관련 테이블 정리 (외래 키 순서 고려)
DELETE FROM p_order_item_shipping;
DELETE FROM p_order_item;
DELETE FROM p_order_audit;
DELETE FROM p_order;

-- NOTE: order-service는 자체 DB를 사용하므로 다른 서비스의 테이블은 정리하지 않음
