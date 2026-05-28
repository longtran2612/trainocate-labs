import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { getTransactionHistory } from '../api/transactionApi';
import { checkBalance } from '../api/accountApi';

const STATUS_MAP = {
  COMPLETED: { label: 'Completed', cls: 'badge-success' },
  PENDING: { label: 'Pending', cls: 'badge-warning' },
  REVERSED: { label: 'Reversed', cls: 'badge-error' },
  FAILED: { label: 'Failed', cls: 'badge-error' },
};

function formatAmount(amount) {
  const num = Number(amount);
  return num.toLocaleString('vi-VN') + ' VND';
}

function formatCurrency(amount) {
  if (amount == null) return '---';
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
}

function formatDate(dateStr) {
  if (!dateStr) return '-';
  const d = new Date(dateStr);
  return d.toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

export default function HistoryPage() {
  const { user } = useAuth();
  const [transactions, setTransactions] = useState([]);
  const [balance, setBalance] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const [historyRes, balanceRes] = await Promise.allSettled([
          getTransactionHistory(user.accountNo),
          checkBalance(user.accountNo),
        ]);
        if (historyRes.status === 'fulfilled') {
          setTransactions(historyRes.value.data.data || []);
        }
        if (balanceRes.status === 'fulfilled') {
          setBalance(balanceRes.value.data.data);
        }
      } catch (err) {
        setError(err.response?.data?.message || 'Failed to load transaction history');
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [user.accountNo]);

  if (loading) {
    return (
      <div className="page">
        <div className="page-loader">
          <div className="spinner" />
          <span>Loading history...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <div className="page-header">
        <h2>Transaction History</h2>
        <span className="text-muted">{transactions.length} transaction(s)</span>
      </div>

      {/* Balance summary */}
      {balance && (
        <div className="balance-hero" style={{ marginBottom: '1.5rem' }}>
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

      {error && <div className="alert alert-error">{error}</div>}

      {transactions.length === 0 && !error ? (
        <div className="history-empty">
          <div className="history-empty-icon">📋</div>
          <p>No transactions found</p>
        </div>
      ) : (
        <div className="history-list">
          {transactions.map((tx) => {
            const isSender = tx.senderAccount === user.accountNo;
            const statusInfo = STATUS_MAP[tx.status] || { label: tx.status, cls: 'badge-info' };
            const counterpartyAccount = isSender ? tx.receiverAccount : tx.senderAccount;
            const counterpartyName = isSender
              ? (tx.receiverName || tx.receiverFullName || '')
              : (tx.senderName || tx.senderFullName || '');

            return (
              <div className="history-item" key={tx.txId}>
                <div className="history-item-left">
                  <div className={`history-direction ${isSender ? 'outgoing' : 'incoming'}`}>
                    {isSender ? '↑' : '↓'}
                  </div>
                  <div className="history-item-info">
                    <div className="history-item-account mono">
                      {counterpartyAccount}
                      {counterpartyName && (
                        <span className="history-item-name"> — {counterpartyName}</span>
                      )}
                    </div>
                    <div className="history-item-desc">
                      {tx.description || tx.txType || '---'}
                    </div>
                    <div className="history-item-date">{formatDate(tx.initiatedAt)}</div>
                  </div>
                </div>
                <div className="history-item-right">
                  <div className={`history-amount ${isSender ? 'outgoing' : 'incoming'}`}>
                    {isSender ? '-' : '+'}{formatAmount(Math.abs(tx.amount))}
                  </div>
                  <span className={`badge ${statusInfo.cls}`}>{statusInfo.label}</span>
                  {tx.balanceAfter != null && (
                    <div className="history-balance-after">
                      Balance: {formatCurrency(tx.balanceAfter)}
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
