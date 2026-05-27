import client from './client';

export const getKycStatus = (accountNo) =>
  client.post('/api/v1/kyc/get-kyc-status', { accountNo });

export const getKycInfo = (accountNo) =>
  client.post('/api/v1/kyc/get-kyc-info', { accountNo });
