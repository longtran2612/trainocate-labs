-- ============================================================
-- Money Transfer Platform - Init Data
-- Run: psql -U postgres -f init-data.sql
-- Prerequisites: databases created via init-db.sql
-- ============================================================

-- Fixed UUIDs for cross-service consistency
-- User 1 (nguyen.vana):  user_id = 11111111-1111-1111-1111-111111111111
-- User 2 (tran.vanb):    user_id = 22222222-2222-2222-2222-222222222222
-- User 3 (le.thic):      user_id = 33333333-3333-3333-3333-333333333333
-- Account 1 (user 1):    account_id = aaaa1111-1111-1111-1111-111111111111, account_no = 1000000001
-- Account 2 (user 2):    account_id = aaaa2222-2222-2222-2222-222222222222, account_no = 1000000002
-- Account 3 (user 3):    account_id = aaaa3333-3333-3333-3333-333333333333, account_no = 1000000003
-- Password for all users: 123456

-- ============================================================
-- AUTH_DB: Users
-- ============================================================
\c auth;

INSERT INTO users (user_id, username, password_hash, phone, email, status, created_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'nguyen.vana',
     '$2a$10$2SZ5O6XDbPYZlfnivvEL4OHvNxrCxsZ6wo4g6m6hxUxG1v4nJaQ9q',
     '0901000001', 'nguyen.vana@email.com', 'ACTIVE', NOW()),

    ('22222222-2222-2222-2222-222222222222', 'tran.vanb',
     '$2a$10$2SZ5O6XDbPYZlfnivvEL4OHvNxrCxsZ6wo4g6m6hxUxG1v4nJaQ9q',
     '0901000002', 'tran.vanb@email.com', 'ACTIVE', NOW()),

    ('33333333-3333-3333-3333-333333333333', 'le.thic',
     '$2a$10$2SZ5O6XDbPYZlfnivvEL4OHvNxrCxsZ6wo4g6m6hxUxG1v4nJaQ9q',
     '0901000003', 'le.thic@email.com', 'ACTIVE', NOW())
ON CONFLICT (user_id) DO NOTHING;

-- ============================================================
-- ACCOUNT_DB: Accounts
-- ============================================================
\c account_db;

INSERT INTO accounts (account_id, account_no, user_id, cif, full_name, dob, mobile, email,
                      balance, available_balance, hold_balance, currency, status, created_at)
VALUES
    ('aaaa1111-1111-1111-1111-111111111111', '1000000001',
     '11111111-1111-1111-1111-111111111111', 'CIF000001',
     'Nguyen Van A', '1990-05-15', '0901000001', 'nguyen.vana@email.com',
     50000000.00, 50000000.00, 0.00, 'VND', 'ACTIVE', NOW()),

    ('aaaa2222-2222-2222-2222-222222222222', '1000000002',
     '22222222-2222-2222-2222-222222222222', 'CIF000002',
     'Tran Van B', '1985-08-20', '0901000002', 'tran.vanb@email.com',
     100000000.00, 100000000.00, 0.00, 'VND', 'ACTIVE', NOW()),

    ('aaaa3333-3333-3333-3333-333333333333', '1000000003',
     '33333333-3333-3333-3333-333333333333', 'CIF000003',
     'Le Thi C', '1992-12-01', '0901000003', 'le.thic@email.com',
     200000000.00, 200000000.00, 0.00, 'VND', 'ACTIVE', NOW())
ON CONFLICT (account_id) DO NOTHING;

-- ============================================================
-- KYC_DB: KYC Records (User 1 & 2 verified, User 3 pending)
-- ============================================================
\c kyc_db;

INSERT INTO kyc_records (kyc_id, user_id, kyc_tier, full_name, id_number, id_type, verified_at, status)
VALUES
    ('bbbb1111-1111-1111-1111-111111111111',
     '11111111-1111-1111-1111-111111111111', 'TIER_2',
     'Nguyen Van A', '001090012345', 'NATIONAL_ID', NOW(), 'VERIFIED'),

    ('bbbb2222-2222-2222-2222-222222222222',
     '22222222-2222-2222-2222-222222222222', 'TIER_1',
     'Tran Van B', '001085067890', 'NATIONAL_ID', NOW(), 'VERIFIED'),

    ('bbbb3333-3333-3333-3333-333333333333',
     '33333333-3333-3333-3333-333333333333', 'TIER_0',
     'Le Thi C', '001092011111', 'NATIONAL_ID', NULL, 'PENDING')
ON CONFLICT (kyc_id) DO NOTHING;

-- ============================================================
-- LIMIT_DB: Account Limits
-- ============================================================
\c limit_db;

INSERT INTO account_limits (limit_id, account_no, kyc_tier, transfer_type,
                            single_limit, daily_limit, monthly_limit,
                            used_daily, used_monthly, reset_at)
VALUES
    -- User 1 (TIER_2): 50M single / 200M daily / 500M monthly
    ('cccc1111-1111-1111-1111-111111111111',
     '1000000001', 'TIER_2', 'INTERNAL',
     50000000.00, 200000000.00, 500000000.00,
     0.00, 0.00, NOW()),

    ('cccc1112-1111-1111-1111-111111111111',
     '1000000001', 'TIER_2', 'EXTERNAL',
     50000000.00, 200000000.00, 500000000.00,
     0.00, 0.00, NOW()),

    -- User 2 (TIER_1): 5M single / 20M daily / 100M monthly
    ('cccc2222-2222-2222-2222-222222222222',
     '1000000002', 'TIER_1', 'INTERNAL',
     5000000.00, 20000000.00, 100000000.00,
     0.00, 0.00, NOW()),

    ('cccc2223-2222-2222-2222-222222222222',
     '1000000002', 'TIER_1', 'EXTERNAL',
     5000000.00, 20000000.00, 100000000.00,
     0.00, 0.00, NOW())
ON CONFLICT (limit_id) DO NOTHING;

-- User 3 has no limits (TIER_0 = no transactions allowed)
