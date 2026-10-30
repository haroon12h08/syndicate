-- V29: Production Hardening — Auth MFA, Lockout, and Storage Metadata (Phase H: H1, H2, H3, spec §50-51)

-- 1. MFA and Lockout fields for users
ALTER TABLE users ADD COLUMN mfa_secret VARCHAR(64);
ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP WITH TIME ZONE;

-- 2. Storage backend tracking for evidence (supports local filesystem and S3/MinIO)
ALTER TABLE evidence ADD COLUMN storage_backend VARCHAR(32) NOT NULL DEFAULT 'FILESYSTEM';
