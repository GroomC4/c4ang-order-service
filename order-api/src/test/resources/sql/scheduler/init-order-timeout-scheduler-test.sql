-- OrderTimeoutScheduler 통합 테스트 데이터 초기화

-- 마이크로서비스 분리 후: Product, Store는 TestProductAdapter, TestStoreAdapter stub 사용
-- 이 파일은 Order Service가 관리하는 테이블만 초기화
-- 실제 테스트 데이터는 테스트 코드에서 직접 생성

-- 빈 스크립트 방지용
SELECT 1;
