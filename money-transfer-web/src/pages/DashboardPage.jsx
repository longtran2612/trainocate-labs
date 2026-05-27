import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import * as accountApi from '../api/accountApi';
import * as kycApi from '../api/kycApi';
import * as limitApi from '../api/limitApi';
import InfoCard from '../components/InfoCard';

function formatCurrency(amount) {
  if (amount == null) return '---';
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
}

function formatDate(dateStr) {
  if (!dateStr) return '---';
  return new Date(dateStr).toLocaleDateString('vi-VN');
}

function formatDateTime(dateStr) {
  if (!dateStr) return '---';
  return new Date(dateStr).toLocaleString('vi-VN');
}

export default function DashboardPage() {
  const { user } = useAuth();
  const [account, setAccount] = useState(null);
  const [balance, setBalance] = useState(null);
  const [kyc, setKyc] = useState(null);
  const [limit, setLimit] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!user?.accountNo) return;
    loadData();
  }, [user?.accountNo]);

  const loadData = async () => {
    setLoading(true);
    setError('');
    try {
      const [accountRes, balanceRes, kycRes, limitRes] = await Promise.allSettled([
        accountApi.getCustomerInfo(user.accountNo),
        accountApi.checkBalance(user.accountNo),
        kycApi.getKycInfo(user.accountNo),
        limitApi.getLimitInfo(user.accountNo),
      ]);

      if (accountRes.status === 'fulfilled') setAccount(accountRes.value.data.data);
      if (balanceRes.status === 'fulfilled') setBalance(balanceRes.value.data.data);
      if (kycRes.status === 'fulfilled') setKyc(kycRes.value.data.data);
      if (limitRes.status === 'fulfilled') setLimit(limitRes.value.data.data);
    } catch (err) {
      setError('Failed to load account data');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="page">
        <div className="page-loader">
          <div className="spinner" />
          <p>Loading dashboard...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <div className="page-header">
        <h2>Dashboard</h2>
        <button className="btn btn-outline btn-sm" onClick={loadData}>
          Refresh
        </button>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {/* Balance Hero */}
      {balance && (
        <div className="balance-hero">
          <div className="balance-label">Available Balance</div>
          <div className="balance-amount">{formatCurrency(balance.availableBalance)}</div>
          <div className="balance-details">
            <span>Total: {formatCurrency(balance.balance)}</span>
            <span className="separator">|</span>
            <span>Hold: {formatCurrency(balance.holdBalance)}</span>
            <span className="separator">|</span>
            <span>{balance.currency}</span>
          </div>
        </div>
      )}

      <div className="card-grid">
        {/* Account Info */}
        <InfoCard title="Account Information" icon="&#x1F464;">
          {account ? (
            <div className="info-rows">
              <div className="info-row">
                <span className="label">Account No</span>
                <span className="value mono">{account.accountNo}</span>
              </div>
              <div className="info-row">
                <span className="label">Full Name</span>
                <span className="value">{account.fullName}</span>
              </div>
              <div className="info-row">
                <span className="label">CIF</span>
                <span className="value mono">{account.cif}</span>
              </div>
              <div className="info-row">
                <span className="label">Mobile</span>
                <span className="value">{account.mobile}</span>
              </div>
              <div className="info-row">
                <span className="label">Email</span>
                <span className="value">{account.email}</span>
              </div>
              <div className="info-row">
                <span className="label">Date of Birth</span>
                <span className="value">{formatDate(account.dob)}</span>
              </div>
              <div className="info-row">
                <span className="label">Address</span>
                <span className="value">{account.address || '---'}</span>
              </div>
              <div className="info-row">
                <span className="label">Status</span>
                <span className={`badge ${account.status === 'ACTIVE' ? 'badge-success' : 'badge-warning'}`}>
                  {account.status}
                </span>
              </div>
              <div className="info-row">
                <span className="label">Created</span>
                <span className="value">{formatDateTime(account.createdAt)}</span>
              </div>
            </div>
          ) : (
            <p className="text-muted">Unable to load account info</p>
          )}
        </InfoCard>

        {/* KYC Info */}
        <InfoCard title="KYC Information" icon="&#x1F6E1;">
          {kyc ? (
            <div className="info-rows">
              <div className="info-row">
                <span className="label">KYC Tier</span>
                <span className={`badge ${kyc.kycTier === 'TIER_3' ? 'badge-success' : 'badge-info'}`}>
                  {kyc.kycTier}
                </span>
              </div>
              <div className="info-row">
                <span className="label">Status</span>
                <span className={`badge ${kyc.status === 'VERIFIED' ? 'badge-success' : 'badge-warning'}`}>
                  {kyc.status}
                </span>
              </div>
              <div className="info-row">
                <span className="label">Full Name</span>
                <span className="value">{kyc.fullName}</span>
              </div>
              <div className="info-row">
                <span className="label">ID Type</span>
                <span className="value">{kyc.idType || '---'}</span>
              </div>
              <div className="info-row">
                <span className="label">ID Number</span>
                <span className="value mono">{kyc.idNumber || '---'}</span>
              </div>
              <div className="info-row">
                <span className="label">Verified At</span>
                <span className="value">{formatDateTime(kyc.verifiedAt)}</span>
              </div>
            </div>
          ) : (
            <p className="text-muted">Unable to load KYC info</p>
          )}
        </InfoCard>

        {/* Limit Info — one card per transfer type */}
        {Array.isArray(limit) && limit.length > 0 ? (
          limit.map((lim, idx) => (
            <InfoCard
              key={idx}
              title={`Limit - ${lim.transferType}`}
              icon={lim.transferType === 'INTERNAL' ? '&#x1F3E6;' : '&#x1F310;'}
            >
              <div className="info-rows">
                <div className="info-row">
                  <span className="label">KYC Tier</span>
                  <span className="value">{lim.kycTier}</span>
                </div>
                <div className="info-row">
                  <span className="label">Single Limit</span>
                  <span className="value">{formatCurrency(lim.singleLimit)}</span>
                </div>
                <div className="info-row">
                  <span className="label">Daily Limit</span>
                  <span className="value">{formatCurrency(lim.dailyLimit)}</span>
                </div>
                <div className="info-row">
                  <span className="label">Monthly Limit</span>
                  <span className="value">{formatCurrency(lim.monthlyLimit)}</span>
                </div>
                <div className="info-row highlight">
                  <span className="label">Used Today</span>
                  <span className="value">{formatCurrency(lim.usedDaily)}</span>
                </div>
                <div className="info-row highlight">
                  <span className="label">Used This Month</span>
                  <span className="value">{formatCurrency(lim.usedMonthly)}</span>
                </div>
              </div>
            </InfoCard>
          ))
        ) : (
          <InfoCard title="Transfer Limits" icon="&#x1F4CA;">
            <p className="text-muted">Unable to load limit info</p>
          </InfoCard>
        )}
      </div>
    </div>
  );
}
