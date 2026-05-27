import client from './client';

export const inquiryReceiver = (accountNo, bankCode) =>
  client.post('/api/v1/transactions/inquiry', {
    accountNo,
    bankCode,
    channel: 'WEB',
  });

export const transfer = (data) =>
  client.post('/api/v1/transactions/transfer', {
    ...data,
    currency: 'VND',
    channel: 'WEB',
  });
