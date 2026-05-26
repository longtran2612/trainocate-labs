-- KYC Service - Sample Data
-- User 1: TIER_2 (verified), User 2: TIER_1 (verified), User 3: TIER_0 (pending)
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
