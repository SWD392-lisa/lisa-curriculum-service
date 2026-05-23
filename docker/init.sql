-- LISA Platform — PostgreSQL init script
-- Chạy tự động khi container postgres khởi động lần đầu

-- Tạo database nếu chưa có (thường không cần vì đã set trong env)
-- CREATE DATABASE lisadb;

-- Extension hỗ trợ UUID (tuỳ chọn)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Log
DO $$
BEGIN
  RAISE NOTICE 'LISA DB initialized at %', now();
END $$;
