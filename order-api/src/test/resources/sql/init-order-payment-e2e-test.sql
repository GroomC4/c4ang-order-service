-- Test data for Karate Order & Payment E2E Test
-- Using fixed UUIDs for predictable testing

-- ============================================================
-- Users (CUSTOMER x2, OWNER x2)
-- ============================================================

-- Customer 1: customer1
-- Password: testpass123! (BCrypt hash)
INSERT INTO p_user (id, username, email, password_hash, role, is_active, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'customer1', 'testcustomer1@karate.com',
        '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'CUSTOMER', true, NOW(), NOW());

INSERT INTO p_user_profile (id, user_id, full_name, phone_number, contact_email, default_address, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-111111111112', '11111111-1111-1111-1111-111111111111',
        'Test Customer 1', '010-1111-1111', 'testcustomer1@karate.com', '서울시 강남구 테헤란로 1', NOW(), NOW());

-- Customer 2: customer2
INSERT INTO p_user (id, username, email, password_hash, role, is_active, created_at, updated_at)
VALUES ('22222222-2222-2222-2222-222222222222', 'customer2', 'testcustomer2@karate.com',
        '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'CUSTOMER', true, NOW(), NOW());

INSERT INTO p_user_profile (id, user_id, full_name, phone_number, contact_email, default_address, created_at, updated_at)
VALUES ('22222222-2222-2222-2222-222222222223', '22222222-2222-2222-2222-222222222222',
        'Test Customer 2', '010-2222-2222', 'testcustomer2@karate.com', '서울시 강남구 테헤란로 2', NOW(), NOW());

-- Owner 1: owner1
INSERT INTO p_user (id, username, email, password_hash, role, is_active, created_at, updated_at)
VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'owner1', 'testowner1@karate.com',
        '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'OWNER', true, NOW(), NOW());

INSERT INTO p_user_profile (id, user_id, full_name, phone_number, contact_email, default_address, created_at, updated_at)
VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'Test Owner 1', '010-1234-5678', 'testowner1@karate.com', '서울시 강남구 역삼동', NOW(), NOW());

-- Owner 2: owner2
INSERT INTO p_user (id, username, email, password_hash, role, is_active, created_at, updated_at)
VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'owner2', 'testowner2@karate.com',
        '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'OWNER', true, NOW(), NOW());

INSERT INTO p_user_profile (id, user_id, full_name, phone_number, contact_email, default_address, created_at, updated_at)
VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        'Test Owner 2', '010-2345-6789', 'testowner2@karate.com', '서울시 강남구 삼성동', NOW(), NOW());

-- ============================================================
-- Stores
-- ============================================================

-- Store 1 (owned by testowner1): 전자제품 스토어
INSERT INTO p_store (id, owner_user_id, name, description, status, created_at, updated_at)
VALUES ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        '전자제품 스토어', 'E2E 테스트용 전자제품 스토어', 'REGISTERED', NOW(), NOW());

INSERT INTO p_store_rating (id, store_id, average_rating, review_count, launched_at, created_at, updated_at)
VALUES ('cccccccc-cccc-cccc-cccc-ccccccccccc1', 'cccccccc-cccc-cccc-cccc-cccccccccccc',
        4.5, 50, NOW(), NOW(), NOW());

-- Store 2 (owned by testowner2): 의류 스토어
INSERT INTO p_store (id, owner_user_id, name, description, status, created_at, updated_at)
VALUES ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        '의류 스토어', 'E2E 테스트용 의류 스토어', 'REGISTERED', NOW(), NOW());

INSERT INTO p_store_rating (id, store_id, average_rating, review_count, launched_at, created_at, updated_at)
VALUES ('dddddddd-dddd-dddd-dddd-ddddddddddd1', 'dddddddd-dddd-dddd-dddd-dddddddddddd',
        4.8, 120, NOW(), NOW(), NOW());

-- ============================================================
-- Product Categories
-- ============================================================

-- Category 1: 전자제품
INSERT INTO p_product_category (id, name, parent_category_id, depth, created_at, updated_at)
VALUES ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', '전자제품', NULL, 0, NOW(), NOW());

-- Category 2: 의류
INSERT INTO p_product_category (id, name, parent_category_id, depth, created_at, updated_at)
VALUES ('ffffffff-ffff-ffff-ffff-ffffffffffff', '의류', NULL, 0, NOW(), NOW());

-- ============================================================
-- Products (충분한 재고 보유)
-- ============================================================

-- Product 1: 무선 마우스 (전자제품 스토어, 충분한 재고)
INSERT INTO p_product (id, store_id, store_name, category_id, product_name, status, price, stock_quantity, thumbnail_url, description, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000001', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '전자제품 스토어', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
        '무선 마우스', 'ON_SALE', 29900.00, 100, 'https://example.com/thumbnails/mouse.jpg',
        '편안한 그립감의 무선 마우스', NOW(), NOW());

INSERT INTO p_product_image (id, product_id, image_type, image_url, display_order, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-00000000001a', '10000000-0000-0000-0000-000000000001',
        'PRIMARY', 'https://example.com/images/mouse_primary.jpg', 0, NOW(), NOW());

-- Product 2: 기계식 키보드 (전자제품 스토어, 충분한 재고)
INSERT INTO p_product (id, store_id, store_name, category_id, product_name, status, price, stock_quantity, thumbnail_url, description, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000002', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '전자제품 스토어', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
        '기계식 키보드', 'ON_SALE', 89900.00, 50, 'https://example.com/thumbnails/keyboard.jpg',
        '청축 스위치 기계식 키보드', NOW(), NOW());

INSERT INTO p_product_image (id, product_id, image_type, image_url, display_order, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-00000000002a', '10000000-0000-0000-0000-000000000002',
        'PRIMARY', 'https://example.com/images/keyboard_primary.jpg', 0, NOW(), NOW());

-- Product 3: 27인치 모니터 (전자제품 스토어, 충분한 재고)
INSERT INTO p_product (id, store_id, store_name, category_id, product_name, status, price, stock_quantity, thumbnail_url, description, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000003', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '전자제품 스토어', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
        '27인치 모니터', 'ON_SALE', 249900.00, 30, 'https://example.com/thumbnails/monitor.jpg',
        'FHD 27인치 게이밍 모니터', NOW(), NOW());

INSERT INTO p_product_image (id, product_id, image_type, image_url, display_order, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-00000000003a', '10000000-0000-0000-0000-000000000003',
        'PRIMARY', 'https://example.com/images/monitor_primary.jpg', 0, NOW(), NOW());

-- Product 4: 티셔츠 (의류 스토어, 충분한 재고)
INSERT INTO p_product (id, store_id, store_name, category_id, product_name, status, price, stock_quantity, thumbnail_url, description, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000004', 'dddddddd-dddd-dddd-dddd-dddddddddddd', '의류 스토어', 'ffffffff-ffff-ffff-ffff-ffffffffffff',
        '코튼 티셔츠', 'ON_SALE', 19900.00, 200, 'https://example.com/thumbnails/tshirt.jpg',
        '부드러운 면 티셔츠', NOW(), NOW());

INSERT INTO p_product_image (id, product_id, image_type, image_url, display_order, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-00000000004a', '10000000-0000-0000-0000-000000000004',
        'PRIMARY', 'https://example.com/images/tshirt_primary.jpg', 0, NOW(), NOW());

-- Product 5: 청바지 (의류 스토어, 충분한 재고)
INSERT INTO p_product (id, store_id, store_name, category_id, product_name, status, price, stock_quantity, thumbnail_url, description, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000005', 'dddddddd-dddd-dddd-dddd-dddddddddddd', '의류 스토어', 'ffffffff-ffff-ffff-ffff-ffffffffffff',
        '청바지', 'ON_SALE', 49900.00, 80, 'https://example.com/thumbnails/jeans.jpg',
        '슬림핏 청바지', NOW(), NOW());

INSERT INTO p_product_image (id, product_id, image_type, image_url, display_order, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-00000000005a', '10000000-0000-0000-0000-000000000005',
        'PRIMARY', 'https://example.com/images/jeans_primary.jpg', 0, NOW(), NOW());

-- Product 6: 재고 없는 상품 (OFF_SHELF, 주문 실패 테스트용)
INSERT INTO p_product (id, store_id, store_name, category_id, product_name, status, price, stock_quantity, thumbnail_url, description, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000006', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '전자제품 스토어', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
        '재고 없는 상품', 'OFF_SHELF', 19900.00, 0, 'https://example.com/thumbnails/soldout.jpg',
        '재고가 없는 상품', NOW(), NOW());
