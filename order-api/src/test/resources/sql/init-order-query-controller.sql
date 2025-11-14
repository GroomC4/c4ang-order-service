-- OrderQueryController 통합 테스트 데이터 초기화

-- 테스트용 카테고리 데이터
INSERT INTO p_product_category (id, name, parent_category_id, depth, created_at, updated_at)
VALUES ('cccccccc-cccc-cccc-cccc-000000000001', '테스트 카테고리', NULL, 0, NOW(), NOW());

-- 테스트용 상점 데이터
INSERT INTO p_store (id, owner_user_id, name, description, status, created_at, updated_at)
VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-000000000001', '11111111-1111-1111-1111-111111111111', '테스트 상점', '통합 테스트용 상점입니다', 'REGISTERED', NOW(), NOW());

-- 테스트용 상품 데이터
INSERT INTO p_product (id, store_id, store_name, category_id, product_name, status, price, stock_quantity, thumbnail_url, description, created_at, updated_at)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-000000000001', 'bbbbbbbb-bbbb-bbbb-bbbb-000000000001', '테스트 상점', 'cccccccc-cccc-cccc-cccc-000000000001', '테스트 상품 1', 'ON_SALE', 10000, 100, 'https://example.com/test1.jpg', '통합 테스트용 상품 1', NOW(), NOW()),
    ('aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'bbbbbbbb-bbbb-bbbb-bbbb-000000000001', '테스트 상점', 'cccccccc-cccc-cccc-cccc-000000000001', '테스트 상품 2', 'ON_SALE', 20000, 50, 'https://example.com/test2.jpg', '통합 테스트용 상품 2', NOW(), NOW());

-- 테스트용 주문 데이터 (CUSTOMER_USER_1 소유: 33333333-3333-3333-3333-333333333333)
-- ORDER_1: STOCK_RESERVED (최신)
INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, reservation_id, expires_at, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-000000000001', '33333333-3333-3333-3333-333333333333', 'bbbbbbbb-bbbb-bbbb-bbbb-000000000001', 'ORD-20251028-001',
        'STOCK_RESERVED', '{}'::json, '[]'::json, 'reservation-001', NOW() + INTERVAL '10 minutes', NOW(), NOW());

-- ORDER_2: PAYMENT_COMPLETED
INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, reservation_id, payment_id, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-000000000002', '33333333-3333-3333-3333-333333333333', 'bbbbbbbb-bbbb-bbbb-bbbb-000000000001', 'ORD-20251028-002',
        'PAYMENT_COMPLETED', '{}'::json, '[]'::json, 'reservation-002', 'dddddddd-dddd-dddd-dddd-000000000002', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

-- ORDER_3: DELIVERED
INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, reservation_id, payment_id, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-000000000003', '33333333-3333-3333-3333-333333333333', 'bbbbbbbb-bbbb-bbbb-bbbb-000000000001', 'ORD-20251028-003',
        'DELIVERED', '{}'::json, '[]'::json, 'reservation-003', 'dddddddd-dddd-dddd-dddd-000000000003', NOW() - INTERVAL '5 days', NOW() - INTERVAL '2 days');

-- ORDER_4: ORDER_CANCELLED
INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, reservation_id, failure_reason, cancelled_at, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-000000000004', '33333333-3333-3333-3333-333333333333', 'bbbbbbbb-bbbb-bbbb-bbbb-000000000001', 'ORD-20251028-004',
        'ORDER_CANCELLED', '{}'::json, '[]'::json, 'reservation-004', '단순 변심', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days');

-- 테스트용 주문 데이터 (CUSTOMER_USER_2 소유: 22222222-2222-2222-2222-222222222222) - 접근 거부 테스트용
-- ORDER_5: PAYMENT_COMPLETED
INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, reservation_id, payment_id, created_at, updated_at)
VALUES ('22222222-2222-2222-2222-000000000001', '22222222-2222-2222-2222-222222222222', 'bbbbbbbb-bbbb-bbbb-bbbb-000000000001', 'ORD-20251028-005',
        'PAYMENT_COMPLETED', '{}'::json, '[]'::json, 'reservation-005', 'dddddddd-dddd-dddd-dddd-000000000005', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days');

-- 주문 항목 데이터 (ORDER_1)
INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity, created_at, updated_at)
VALUES
    ('11111111-1111-1111-aaaa-000000000001', '11111111-1111-1111-1111-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000001', '테스트 상품 1', 10000, 1, NOW(), NOW()),
    ('11111111-1111-1111-aaaa-000000000002', '11111111-1111-1111-1111-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', '테스트 상품 2', 20000, 1, NOW(), NOW());

-- 주문 항목 데이터 (ORDER_2)
INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity, created_at, updated_at)
VALUES ('11111111-1111-1111-aaaa-000000000003', '11111111-1111-1111-1111-000000000002', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', '테스트 상품 2', 20000, 1, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

-- 주문 항목 데이터 (ORDER_3)
INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity, created_at, updated_at)
VALUES
    ('11111111-1111-1111-aaaa-000000000004', '11111111-1111-1111-1111-000000000003', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000001', '테스트 상품 1', 10000, 3, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
    ('11111111-1111-1111-aaaa-000000000005', '11111111-1111-1111-1111-000000000003', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', '테스트 상품 2', 20000, 1, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days');

-- 주문 항목 데이터 (ORDER_4)
INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity, created_at, updated_at)
VALUES ('11111111-1111-1111-aaaa-000000000006', '11111111-1111-1111-1111-000000000004', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000001', '테스트 상품 1', 10000, 1, NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days');

-- 주문 항목 데이터 (ORDER_5 - CUSTOMER_USER_2 소유)
INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity, created_at, updated_at)
VALUES
    ('22222222-2222-2222-aaaa-000000000001', '22222222-2222-2222-2222-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000001', '테스트 상품 1', 10000, 2, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
    ('22222222-2222-2222-aaaa-000000000002', '22222222-2222-2222-2222-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', '테스트 상품 2', 20000, 1, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days');

-- 주문 감사 로그는 비즈니스 로직 통합 테스트에 필수적이지 않으므로 생략
