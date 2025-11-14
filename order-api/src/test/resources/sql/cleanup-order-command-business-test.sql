-- OrderCommandController 비즈니스 로직 통합 테스트 데이터 정리

-- 주문 관련 테이블 정리 (외래 키 순서 고려)
-- 모든 주문 데이터 삭제 (테스트에서 동적으로 생성된 주문 포함)
DELETE FROM p_order_item;
DELETE FROM p_order_audit;
DELETE FROM p_order;

-- 상품 정리
DELETE FROM p_product WHERE id IN (
    'aaaaaaaa-aaaa-aaaa-aaaa-000000000001',
    'aaaaaaaa-aaaa-aaaa-aaaa-000000000002',
    'aaaaaaaa-aaaa-aaaa-aaaa-000000000003'
);

-- 상점 정리
DELETE FROM p_store WHERE id IN (
    'bbbbbbbb-bbbb-bbbb-bbbb-000000000001',
    'bbbbbbbb-bbbb-bbbb-bbbb-000000000002'
);

-- 카테고리 정리
DELETE FROM p_product_category WHERE id = 'cccccccc-cccc-cccc-cccc-000000000001';

-- 테스트에서 생성한 사용자 정리
-- 사용자 프로필 먼저 삭제
DELETE FROM p_user_profile WHERE user_id IN (
    SELECT id FROM p_user WHERE email LIKE '%order-test@example.com'
);

-- Refresh Token 삭제
DELETE FROM p_user_refresh_token WHERE user_id IN (
    SELECT id FROM p_user WHERE email LIKE '%order-test@example.com'
);

-- 사용자 삭제
DELETE FROM p_user WHERE email LIKE '%order-test@example.com';
