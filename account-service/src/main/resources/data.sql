-- Account Service - Sample Data
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
