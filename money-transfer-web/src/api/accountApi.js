import client from './client';

export const getCustomerInfo = (accountNo) =>
  client.post('/api/v1/accounts/get-customer-info', { accountNo });

export const checkBalance = (accountNo) =>
  client.post('/api/v1/accounts/check-balance', { accountNo });

export const inquiry = (accountNo) =>
  client.post('/api/v1/accounts/inquiry', { accountNo });

export const getAllAccounts = () =>
  client.get('/api/v1/accounts');
