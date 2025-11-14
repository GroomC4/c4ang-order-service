-- OrderCommandController 비즈니스 로직 통합 테스트 데이터 초기화

-- 테스트용 카테고리 데이터
INSERT INTO p_product_category (id, name, parent_category_id, depth, created_at, updated_at)
VALUES ('cccccccc-cccc-cccc-cccc-000000000001', '전자제품', NULL, 0, NOW(), NOW());

-- 테스트용 상점 데이터 (owner_user_id는 테스트에서 생성한 사용자 ID를 사용)
-- 여기서는 임시로 UUID를 사용하지만, 실제로는 테스트에서 생성한 사용자 ID로 업데이트됩니다
INSERT INTO p_store (id, owner_user_id, name, description, status, created_at, updated_at)
VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-000000000001', '11111111-1111-1111-1111-111111111111', '테크 스토어', '전자제품 전문 스토어', 'REGISTERED', NOW(), NOW()),
    ('bbbbbbbb-bbbb-bbbb-bbbb-000000000002', '22222222-2222-2222-2222-222222222222', '패션 스토어', '의류 전문 스토어', 'REGISTERED', NOW(), NOW());

-- 테스트용 상품 데이터
INSERT INTO p_product (id, store_id, store_name, category_id, product_name, status, price, stock_quantity, thumbnail_url, description, created_at, updated_at)
VALUES
    -- 테크 스토어 상품 (재고 충분)
    ('aaaaaaaa-aaaa-aaaa-aaaa-000000000001', 'bbbbbbbb-bbbb-bbbb-bbbb-000000000001', '테크 스토어', 'cccccccc-cccc-cccc-cccc-000000000001', '무선 마우스', 'ON_SALE', 50000, 100, 'https://example.com/mouse.jpg', '고성능 무선 마우스', NOW(), NOW()),
    ('aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'bbbbbbbb-bbbb-bbbb-bbbb-000000000001', '테크 스토어', 'cccccccc-cccc-cccc-cccc-000000000001', '기계식 키보드', 'ON_SALE', 120000, 50, 'https://example.com/keyboard.jpg', '청축 기계식 키보드', NOW(), NOW()),
    -- 재고가 적은 상품 (재고 부족 테스트용)
    ('aaaaaaaa-aaaa-aaaa-aaaa-000000000003', 'bbbbbbbb-bbbb-bbbb-bbbb-000000000001', '테크 스토어', 'cccccccc-cccc-cccc-cccc-000000000001', 'USB 케이블', 'ON_SALE', 10000, 2, 'https://example.com/cable.jpg', 'USB-C 케이블', NOW(), NOW());
