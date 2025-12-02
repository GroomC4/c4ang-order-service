-- OrderCommandController 통합 테스트 데이터 초기화

-- 마이크로서비스 분리 후: Product, Store, User는 TestProductAdapter, TestStoreAdapter stub 사용
-- Order Service는 p_order, p_order_item, p_order_audit만 관리

-- 테스트용 주문 데이터 (CUSTOMER_USER_1 소유: 33333333-3333-3333-3333-333333333333)
-- ORDER_2: PAYMENT_COMPLETED (취소 가능)
INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, reservation_id, payment_id, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-000000000002', '33333333-3333-3333-3333-333333333333', 'bbbbbbbb-bbbb-bbbb-bbbb-000000000001', 'ORD-20251028-002',
        'PAYMENT_COMPLETED', '{}'::json, '[]'::json, 'reservation-002', 'dddddddd-dddd-dddd-dddd-000000000002', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

-- ORDER_3: DELIVERED (환불 가능)
INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, reservation_id, payment_id, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-000000000003', '33333333-3333-3333-3333-333333333333', 'bbbbbbbb-bbbb-bbbb-bbbb-000000000001', 'ORD-20251028-003',
        'DELIVERED', '{}'::json, '[]'::json, 'reservation-003', 'dddddddd-dddd-dddd-dddd-000000000003', NOW() - INTERVAL '5 days', NOW() - INTERVAL '2 days');

-- 주문 항목 데이터 (ORDER_2)
INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity, created_at, updated_at)
VALUES ('11111111-1111-1111-aaaa-000000000003', '11111111-1111-1111-1111-000000000002', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', '테스트 상품 2', 20000, 1, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

-- 주문 항목 데이터 (ORDER_3)
INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity, created_at, updated_at)
VALUES
    ('11111111-1111-1111-aaaa-000000000004', '11111111-1111-1111-1111-000000000003', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000001', '테스트 상품 1', 10000, 3, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
    ('11111111-1111-1111-aaaa-000000000005', '11111111-1111-1111-1111-000000000003', 'aaaaaaaa-aaaa-aaaa-aaaa-000000000002', '테스트 상품 2', 20000, 1, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days');
