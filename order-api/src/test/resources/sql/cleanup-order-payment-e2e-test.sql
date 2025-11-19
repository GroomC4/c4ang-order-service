-- Cleanup test data for Karate Order & Payment E2E Test

-- ============================================================
-- Delete in reverse order of dependencies
-- ============================================================

-- Delete Payment History (child of Payment)
DELETE FROM p_payment_history
WHERE payment_id IN (
    SELECT id FROM p_payment WHERE order_id IN (
        SELECT id FROM p_order WHERE user_id IN (
            '11111111-1111-1111-1111-111111111111',
            '22222222-2222-2222-2222-222222222222'
        )
    )
);

-- Delete Payments
DELETE FROM p_payment
WHERE order_id IN (
    SELECT id FROM p_order WHERE user_id IN (
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222'
    )
);

-- Delete Order Items
DELETE FROM p_order_item
WHERE order_id IN (
    SELECT id FROM p_order WHERE user_id IN (
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222'
    )
);

-- Delete Orders
DELETE FROM p_order
WHERE user_id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222'
);

-- Delete Product Images
WHERE product_id IN (
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000004',
    '10000000-0000-0000-0000-000000000005',
    '10000000-0000-0000-0000-000000000006'
);

-- Delete Products
WHERE id IN (
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000004',
    '10000000-0000-0000-0000-000000000005',
    '10000000-0000-0000-0000-000000000006'
);

-- Delete Store Ratings
WHERE store_id IN (
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    'dddddddd-dddd-dddd-dddd-dddddddddddd'
);

-- Delete Stores
WHERE id IN (
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    'dddddddd-dddd-dddd-dddd-dddddddddddd'
);

-- Delete Product Categories
WHERE id IN (
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    'ffffffff-ffff-ffff-ffff-ffffffffffff'
);

-- Delete User Profiles
WHERE user_id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
);

-- Delete Users
WHERE id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
);

-- NOTE: order-service는 자체 DB를 사용하므로 다른 서비스의 테이블(p_product, p_store, p_user 등)은 정리하지 않음
