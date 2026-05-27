import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { getTransactionHistory } from '../api/transactionApi';

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
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchHistory = async () => {
      try {
        setLoading(true);
        const res = await getTransactionHistory(user.accountNo);
        setTransactions(res.data.data || []);
      } catch (err) {
        setError(err.response?.data?.message || 'Failed to load transaction history');
      } finally {
        setLoading(false);
      }
    };
    fetchHistory();
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

            return (
              <div className="history-item" key={tx.txId}>
                <div className="history-item-left">
                  <div className={`history-direction ${isSender ? 'outgoing' : 'incoming'}`}>
                    {isSender ? '↑' : '↓'}
                  </div>
                  <div className="history-item-info">
                    <div className="history-item-account mono">
                      {isSender ? tx.receiverAccount : tx.senderAccount}
                    </div>
                    <div className="history-item-desc">
                      {tx.description || tx.txType}
                    </div>
                    <div className="history-item-date">{formatDate(tx.initiatedAt)}</div>
                  </div>
                </div>
                <div className="history-item-right">
                  <div className={`history-amount ${isSender ? 'outgoing' : 'incoming'}`}>
                    {isSender ? '-' : '+'}{formatAmount(Math.abs(tx.amount))}
                  </div>
                  <span className={`badge ${statusInfo.cls}`}>{statusInfo.label}</span>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
