import client from './client';

// Step 1: Register auth user (temp username)
export const register = (password, phone, email) =>
  client.post('/api/v1/auth/register', { password, phone, email });

// Step 2: Create bank account
export const createAccount = (userId, fullName, mobile, email, dob, address) =>
  client.post('/api/v1/accounts', {
    userId,
    cif: `CIF${Date.now()}`,
    fullName,
    mobile,
    email,
    dob: dob || null,
    address: address || null,
    currency: 'VND',
  });

// Step 3: Update auth username to accountNo
export const updateUsername = (userId, newUsername) =>
  client.post('/api/v1/auth/update-username', { userId, newUsername });

// Step 4: KYC verify
export const kycVerify = (userId, accountNo, fullName, idNumber, idType) =>
  client.post('/api/v1/kyc/verify', { userId, accountNo, fullName, idNumber, idType });

// Step 5: Init transfer limits
export const initLimits = (accountNo) =>
  client.post('/api/v1/limits/init', { accountNo });

// Step 6: Credit welcome bonus
export const creditWelcomeBonus = (accountNo) =>
  client.post('/api/v1/accounts/credit', {
    accountNo,
    amount: 10000000,
    referenceId: `WELCOME-${Date.now()}`,
    description: 'Welcome bonus',
  });
