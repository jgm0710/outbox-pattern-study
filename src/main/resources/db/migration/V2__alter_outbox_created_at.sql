-- outbox 테이블의 created_at 컬럼 타입을 TIMESTAMP WITH TIME ZONE에서 BIGINT로 변경
ALTER TABLE outbox ALTER COLUMN created_at TYPE BIGINT USING extract(epoch from created_at) * 1000;
