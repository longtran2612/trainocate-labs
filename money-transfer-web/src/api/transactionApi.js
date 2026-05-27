import client from './client';

export const getTransactionHistory = (accountNo) =>
  client.post('/api/v1/transactions/history', { accountNo });
