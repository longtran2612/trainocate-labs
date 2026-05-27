import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  if (!user) return null;

  return (
    <nav className="navbar">
      <div className="navbar-brand">
        <span className="brand-icon">$</span>
        MoneyTransfer
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
      </div>
      <div className="navbar-user">
        <span className="account-badge">{user.accountNo}</span>
        <button className="btn btn-outline btn-sm" onClick={handleLogout}>
          Logout
        </button>
      </div>
    </nav>
  );
}
