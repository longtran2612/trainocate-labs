-- Limit Service - Sample Data
-- User 1 (TIER_2): 50M/200M/500M, User 2 (TIER_1): 5M/20M/100M
-- User 3 has no limits (TIER_0 = cannot transact)
INSERT INTO account_limits (limit_id, account_no, kyc_tier, transfer_type,
                            single_limit, daily_limit, monthly_limit,
                            used_daily, used_monthly, reset_at)
VALUES
    ('cccc1111-1111-1111-1111-111111111111',
     '1000000001', 'TIER_2', 'INTERNAL',
     50000000.00, 200000000.00, 500000000.00, 0.00, 0.00, NOW()),
    ('cccc1112-1111-1111-1111-111111111111',
     '1000000001', 'TIER_2', 'EXTERNAL',
     50000000.00, 200000000.00, 500000000.00, 0.00, 0.00, NOW()),
    ('cccc2222-2222-2222-2222-222222222222',
     '1000000002', 'TIER_1', 'INTERNAL',
     5000000.00, 20000000.00, 100000000.00, 0.00, 0.00, NOW()),
    ('cccc2223-2222-2222-2222-222222222222',
     '1000000002', 'TIER_1', 'EXTERNAL',
     5000000.00, 20000000.00, 100000000.00, 0.00, 0.00, NOW())
ON CONFLICT (limit_id) DO NOTHING;
