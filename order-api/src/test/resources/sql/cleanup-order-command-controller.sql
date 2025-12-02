-- OrderCommandController 통합 테스트 데이터 정리

-- 마이크로서비스 분리 후: Product, Store, User는 각자의 서비스에서 관리
-- Order Service는 p_order, p_order_item, p_order_audit만 정리

-- 주문 관련 테이블 정리 (외래 키 순서 고려)
DELETE FROM p_order_item WHERE order_id IN (
    '11111111-1111-1111-1111-000000000002',
    '11111111-1111-1111-1111-000000000003'
);

DELETE FROM p_order_audit WHERE order_id IN (
    '11111111-1111-1111-1111-000000000002',
    '11111111-1111-1111-1111-000000000003'
);

DELETE FROM p_order WHERE id IN (
    '11111111-1111-1111-1111-000000000002',
    '11111111-1111-1111-1111-000000000003'
);
