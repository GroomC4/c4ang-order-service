-- OrderCommandController 비즈니스 로직 통합 테스트 데이터 정리

-- 마이크로서비스 분리 후: Product, Store, User는 각자의 서비스에서 관리
-- Order Service는 p_order, p_order_item, p_order_audit만 정리

-- 주문 관련 테이블 정리 (외래 키 순서 고려)
-- 모든 주문 데이터 삭제 (테스트에서 동적으로 생성된 주문 포함)
DELETE FROM p_order_item;
DELETE FROM p_order_audit;
DELETE FROM p_order;
