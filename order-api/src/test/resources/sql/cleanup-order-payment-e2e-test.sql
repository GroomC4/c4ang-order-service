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
DELETE FROM p_product_image
WHERE product_id IN (
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000004',
    '10000000-0000-0000-0000-000000000005',
    '10000000-0000-0000-0000-000000000006'
);

-- Delete Products
DELETE FROM p_product
WHERE id IN (
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000004',
    '10000000-0000-0000-0000-000000000005',
    '10000000-0000-0000-0000-000000000006'
);

-- Delete Store Ratings
DELETE FROM p_store_rating
WHERE store_id IN (
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    'dddddddd-dddd-dddd-dddd-dddddddddddd'
);

-- Delete Stores
DELETE FROM p_store
WHERE id IN (
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    'dddddddd-dddd-dddd-dddd-dddddddddddd'
);

-- Delete Product Categories
DELETE FROM p_product_category
WHERE id IN (
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    'ffffffff-ffff-ffff-ffff-ffffffffffff'
);

-- Delete User Profiles
DELETE FROM p_user_profile
WHERE user_id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
);

-- Delete Users
DELETE FROM p_user
WHERE id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
);
