-- Contract Test 데이터
-- Internal API Contract 테스트에 필요한 주문 데이터

-- 1. ORDER_CONFIRMED 상태 주문 (shouldGetOrder, shouldCheckHasPayment 테스트용)
-- orderId: 550e8400-e29b-41d4-a716-446655440000
INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, note, reservation_id, created_at, updated_at)
VALUES (
    '550e8400-e29b-41d4-a716-446655440000',
    '660e8400-e29b-41d4-a716-446655440000',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'ORD-2024-001',
    'ORDER_CONFIRMED',
    '{}',
    '[{"status":"ORDER_CONFIRMED","timestamp":"2024-01-01T00:00:00","description":"주문 확정"}]',
    'Contract Test 주문',
    'RES-001',
    NOW(),
    NOW()
);

INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity, created_at, updated_at)
VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    '550e8400-e29b-41d4-a716-446655440000',
    '770e8400-e29b-41d4-a716-446655440000',
    '테스트 상품',
    25000,
    2,
    NOW(),
    NOW()
);

-- 2. ORDER_CREATED 상태 주문 (shouldReturn409WhenOrderNotConfirmed 테스트용)
-- orderId: 550e8400-e29b-41d4-a716-446655440001
INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, note, created_at, updated_at)
VALUES (
    '550e8400-e29b-41d4-a716-446655440001',
    '660e8400-e29b-41d4-a716-446655440000',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'ORD-2024-002',
    'ORDER_CREATED',
    '{}',
    '[{"status":"ORDER_CREATED","timestamp":"2024-01-01T00:00:00","description":"주문 생성"}]',
    'Contract Test 주문 (ORDER_CREATED)',
    NOW(),
    NOW()
);

INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity, created_at, updated_at)
VALUES (
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    '550e8400-e29b-41d4-a716-446655440001',
    '770e8400-e29b-41d4-a716-446655440000',
    '테스트 상품',
    25000,
    1,
    NOW(),
    NOW()
);

-- 3. ORDER_CONFIRMED 상태지만 이미 결제가 연결된 주문 (shouldReturn409WhenPaymentAlreadyExists 테스트용)
-- orderId: 550e8400-e29b-41d4-a716-446655440002
INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, note, payment_id, created_at, updated_at)
VALUES (
    '550e8400-e29b-41d4-a716-446655440002',
    '660e8400-e29b-41d4-a716-446655440000',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'ORD-2024-003',
    'ORDER_CONFIRMED',
    '{}',
    '[{"status":"ORDER_CONFIRMED","timestamp":"2024-01-01T00:00:00","description":"주문 확정"}]',
    'Contract Test 주문 (결제 연결됨)',
    '990e8400-e29b-41d4-a716-446655440000',
    NOW(),
    NOW()
);

INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity, created_at, updated_at)
VALUES (
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    '550e8400-e29b-41d4-a716-446655440002',
    '770e8400-e29b-41d4-a716-446655440000',
    '테스트 상품',
    25000,
    1,
    NOW(),
    NOW()
);

-- 4. ORDER_CONFIRMED 상태 주문 (shouldMarkPaymentPending 테스트용 - 상태 변경 테스트)
-- orderId: 550e8400-e29b-41d4-a716-446655440003
INSERT INTO p_order (id, user_id, store_id, order_number, status, payment_summary, timeline, note, reservation_id, created_at, updated_at)
VALUES (
    '550e8400-e29b-41d4-a716-446655440003',
    '660e8400-e29b-41d4-a716-446655440000',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'ORD-2024-004',
    'ORDER_CONFIRMED',
    '{}',
    '[{"status":"ORDER_CONFIRMED","timestamp":"2024-01-01T00:00:00","description":"주문 확정"}]',
    'Contract Test 주문 (결제 대기 변경용)',
    'RES-004',
    NOW(),
    NOW()
);

INSERT INTO p_order_item (id, order_id, product_id, product_name, unit_price, quantity, created_at, updated_at)
VALUES (
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    '550e8400-e29b-41d4-a716-446655440003',
    '770e8400-e29b-41d4-a716-446655440000',
    '테스트 상품',
    25000,
    1,
    NOW(),
    NOW()
);
