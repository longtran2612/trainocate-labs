import { createContext, useContext, useState, useEffect } from 'react';
import * as authApi from '../api/authApi';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    const accountNo = localStorage.getItem('accountNo');
    const userId = localStorage.getItem('userId');
    if (token && accountNo) {
      setUser({ accessToken: token, accountNo, userId });
    }
    setLoading(false);
  }, []);

  const login = async (accountNo, password) => {
    // username = accountNo
    const res = await authApi.login(accountNo, password);
    const { accessToken, refreshToken, userId } = res.data.data;

    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    localStorage.setItem('userId', userId);
    localStorage.setItem('accountNo', accountNo);

    setUser({ accessToken, accountNo, userId });
    return { accountNo, userId };
  };

  const logout = async () => {
    try {
      await authApi.logout();
    } catch {
      // ignore
    }
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('userId');
    localStorage.removeItem('accountNo');
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, login, logout, loading }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
