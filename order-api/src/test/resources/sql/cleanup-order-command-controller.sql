-- OrderCommandController 통합 테스트 데이터 정리

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


-- NOTE: order-service는 자체 DB를 사용하므로 다른 서비스의 테이블(p_product, p_store, p_user 등)은 정리하지 않음
