import client from './client';

export const login = (username, password) =>
  client.post('/api/v1/auth/login', { username, password, deviceId: 'web-portal' });

export const logout = () =>
  client.post('/api/v1/auth/logout');

export const refreshToken = (refreshToken) =>
  client.post('/api/v1/auth/refresh-token', { refreshToken });
