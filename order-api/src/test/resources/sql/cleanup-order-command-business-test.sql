-- OrderCommandController 비즈니스 로직 통합 테스트 데이터 정리

-- 주문 관련 테이블 정리 (외래 키 순서 고려)
-- 모든 주문 데이터 삭제 (테스트에서 동적으로 생성된 주문 포함)
DELETE FROM p_order_item_shipping;
DELETE FROM p_order_item;
DELETE FROM p_order_audit;
DELETE FROM p_order;

-- NOTE: order-service는 자체 DB를 사용하므로 다른 서비스의 테이블(p_product, p_store, p_user 등)은 정리하지 않음
-- 상품, 상점, 사용자 정보는 Feign Client를 통해 조회하거나 Mock으로 처리
