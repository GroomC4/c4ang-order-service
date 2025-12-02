-- OrderTimeoutScheduler 통합 테스트 데이터 정리

-- Order Service가 관리하는 테이블만 정리
DELETE FROM p_order_item_shipping;
DELETE FROM p_order_item;
DELETE FROM p_order_audit;
DELETE FROM p_order;
