-- Auth Service - Sample Data (password: 123456, username = account number)
INSERT INTO users (user_id, username, password_hash, phone, email, status, created_at)
VALUES
    ('11111111-1111-1111-1111-111111111112', '1000000001',
     '$2a$10$2SZ5O6XDbPYZlfnivvEL4OHvNxrCxsZ6wo4g6m6hxUxG1v4nJaQ9q',
     '0901000001', 'nguyen.vana@email.com', 'ACTIVE', NOW()),
    ('22222222-2222-2222-2222-222222222223', '1000000002',
     '$2a$10$2SZ5O6XDbPYZlfnivvEL4OHvNxrCxsZ6wo4g6m6hxUxG1v4nJaQ9q',
     '0901000002', 'tran.vanb@email.com', 'ACTIVE', NOW()),
    ('33333333-3333-3333-3333-333333333334', '1000000003',
     '$2a$10$2SZ5O6XDbPYZlfnivvEL4OHvNxrCxsZ6wo4g6m6hxUxG1v4nJaQ9q',
     '0901000003', 'le.thic@email.com', 'ACTIVE', NOW())
ON CONFLICT (user_id) DO NOTHING;
