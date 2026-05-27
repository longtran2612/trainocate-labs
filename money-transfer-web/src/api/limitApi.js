import client from './client';

export const getLimitInfo = (accountNo) =>
  client.post('/api/v1/limits/limit-info', { accountNo });
