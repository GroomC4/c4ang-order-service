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

-- 상품 정리
DELETE FROM p_product WHERE id IN (
    'aaaaaaaa-aaaa-aaaa-aaaa-000000000001',
    'aaaaaaaa-aaaa-aaaa-aaaa-000000000002'
);

-- 상점 정리
DELETE FROM p_store WHERE id = 'bbbbbbbb-bbbb-bbbb-bbbb-000000000001';

-- 카테고리 정리
DELETE FROM p_product_category WHERE id = 'cccccccc-cccc-cccc-cccc-000000000001';
