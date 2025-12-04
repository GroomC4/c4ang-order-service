-- OrderTimeoutScheduler 통합 테스트 데이터 초기화

-- 마이크로서비스 분리 후: Product, Store는 TestProductAdapter, TestStoreAdapter stub 사용
-- Order Service가 관리하는 테이블만 초기화

-- 테스트 상수:
-- EXPIRED_ORDER_ID: eeeeeeee-eeee-eeee-eeee-111111111111
-- ACTIVE_ORDER_ID: eeeeeeee-eeee-eeee-eeee-222222222222
-- TEST_PRODUCT_ID: aaaaaaaa-aaaa-aaaa-aaaa-000000000001
-- STORE_ID: bbbbbbbb-bbbb-bbbb-bbbb-000000000001
-- USER_ID: 33333333-3333-3333-3333-333333333333

-- 만료된 주문 (expires_at이 과거)
INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline,
                     reservation_id, expires_at, created_at, updated_at)
VALUES ('eeeeeeee-eeee-eeee-eeee-111111111111',
        '33333333-3333-3333-3333-333333333333',
        'bbbbbbbb-bbbb-bbbb-bbbb-000000000001',
        'ORD-EXPIRED-001',
        'PAYMENT_PENDING',
        '{}'::json,
        '[]'::json,
        'RES-EXPIRED-001',
        NOW() - INTERVAL '1 hour',  -- 1시간 전에 만료됨
        NOW() - INTERVAL '2 hours',
        NOW() - INTERVAL '2 hours');

-- 만료된 주문의 아이템
INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity, created_at, updated_at)
VALUES ('eeeeeeee-eeee-eeee-aaaa-111111111111',
        'eeeeeeee-eeee-eeee-eeee-111111111111',
        'aaaaaaaa-aaaa-aaaa-aaaa-000000000001',
        'Test Product',
        10000,
        10,
        NOW() - INTERVAL '2 hours',
        NOW() - INTERVAL '2 hours');

-- 유효한 주문 (expires_at이 미래)
INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline,
                     reservation_id, expires_at, created_at, updated_at)
VALUES ('eeeeeeee-eeee-eeee-eeee-222222222222',
        '33333333-3333-3333-3333-333333333333',
        'bbbbbbbb-bbbb-bbbb-bbbb-000000000001',
        'ORD-ACTIVE-002',
        'PAYMENT_PENDING',
        '{}'::json,
        '[]'::json,
        'RES-ACTIVE-002',
        NOW() + INTERVAL '30 minutes',  -- 30분 후에 만료됨
        NOW() - INTERVAL '5 minutes',
        NOW() - INTERVAL '5 minutes');

-- 유효한 주문의 아이템
INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity, created_at, updated_at)
VALUES ('eeeeeeee-eeee-eeee-aaaa-222222222222',
        'eeeeeeee-eeee-eeee-eeee-222222222222',
        'aaaaaaaa-aaaa-aaaa-aaaa-000000000001',
        'Test Product',
        10000,
        5,
        NOW() - INTERVAL '5 minutes',
        NOW() - INTERVAL '5 minutes');
