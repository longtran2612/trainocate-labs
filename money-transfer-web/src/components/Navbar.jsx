import { useEffect, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getCustomerInfo, checkBalance } from '../api/accountApi';

function formatCurrency(amount) {
  if (amount == null) return '---';
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
}

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [fullName, setFullName] = useState('');
  const [balance, setBalance] = useState(null);

  useEffect(() => {
    if (!user?.accountNo) return;
    Promise.allSettled([
      getCustomerInfo(user.accountNo),
      checkBalance(user.accountNo),
    ]).then(([infoRes, balRes]) => {
      if (infoRes.status === 'fulfilled') {
        setFullName(infoRes.value.data.data?.fullName || '');
      }
      if (balRes.status === 'fulfilled') {
        setBalance(balRes.value.data.data?.availableBalance ?? null);
      }
    });
  }, [user?.accountNo]);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  if (!user) return null;

  return (
    <nav className="navbar">
      <div className="navbar-brand">
        VikkiBank
      </div>
      <div className="navbar-links">
        <NavLink to="/dashboard" className={({ isActive }) => isActive ? 'active' : ''}>
          Dashboard
        </NavLink>
        <NavLink to="/transfer" className={({ isActive }) => isActive ? 'active' : ''}>
          Transfer
        </NavLink>
        <NavLink to="/history" className={({ isActive }) => isActive ? 'active' : ''}>
          History
        </NavLink>
        <NavLink to="/architecture" className={({ isActive }) => isActive ? 'active' : ''}>
          Architecture
        </NavLink>
      </div>
      <div className="navbar-user">
        <div className="navbar-account-info">
          {fullName && <span className="navbar-fullname">{fullName}</span>}
          <div className="navbar-sub">
            <span className="account-badge">{user.accountNo}</span>
            {balance !== null && (
              <>
                <span style={{ color: 'var(--border)', fontSize: '0.7rem' }}>|</span>
                <span className="navbar-balance">{formatCurrency(balance)}</span>
              </>
            )}
          </div>
        </div>
        <button className="btn btn-outline btn-sm" onClick={handleLogout}>
          Logout
        </button>
      </div>
    </nav>
  );
}
