-- OrderCommandController 비즈니스 로직 통합 테스트 데이터 초기화

-- 마이크로서비스 분리 후: Product, Store는 TestProductAdapter, TestStoreAdapter stub 사용
-- 이 파일은 Order Service가 관리하는 테이블만 초기화
-- (필요시 Order 관련 테스트 데이터를 여기에 추가)

-- 빈 스크립트 방지용 (Spring이 빈 스크립트를 허용하지 않음)
SELECT 1;
