import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const [accountNo, setAccountNo] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login(accountNo, password);
      navigate('/dashboard');
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Login failed';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const fillDemo = (accNo) => {
    setAccountNo(accNo);
    setPassword('123456');
    setError('');
  };

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <div className="login-logo">$</div>
          <h1>MoneyTransfer</h1>
          <p>Sign in to your account</p>
        </div>

        <form onSubmit={handleSubmit} className="login-form">
          {error && <div className="alert alert-error">{error}</div>}

          <div className="form-group">
            <label htmlFor="accountNo">Account Number</label>
            <input
              id="accountNo"
              type="text"
              value={accountNo}
              onChange={(e) => setAccountNo(e.target.value)}
              placeholder="Enter account number"
              required
              autoFocus
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter password"
              required
            />
          </div>

          <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>

        <div className="onboard-login-link" style={{ textAlign: 'center', marginBottom: '1rem' }}>
          Don't have an account?{' '}
          <a href="#" onClick={(e) => { e.preventDefault(); navigate('/onboard'); }}>Open Account</a>
        </div>

        <div className="demo-accounts">
          <p>Demo accounts (password: <code>123456</code>)</p>
          <div className="demo-chips">
            <button className="chip" onClick={() => fillDemo('1000000001')}>
              1000000001 - Nguyen Van A
            </button>
            <button className="chip" onClick={() => fillDemo('1000000002')}>
              1000000002 - Tran Van B
            </button>
            <button className="chip" onClick={() => fillDemo('1000000003')}>
              1000000003 - Le Thi C
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
