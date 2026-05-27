import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import * as transferApi from '../api/transferApi';
import * as limitApi from '../api/limitApi';

function formatCurrency(amount) {
  if (amount == null) return '---';
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
}

function formatAmountDisplay(raw) {
  const digits = raw.replace(/\D/g, '');
  if (!digits) return '';
  return parseInt(digits, 10).toLocaleString('vi-VN');
}

function parseAmountRaw(display) {
  return display.replace(/[^0-9]/g, '');
}

const STEPS = { FORM: 0, CONFIRM: 1, RESULT: 2 };

const INTERNAL_BANK_CODE = '970406';

const EXTERNAL_BANKS = [
  { code: 'VCB', name: 'Vietcombank' },
  { code: 'TCB', name: 'Techcombank' },
  { code: 'MBB', name: 'MB Bank' },
  { code: 'VPB', name: 'VPBank' },
  { code: 'ACB', name: 'ACB' },
  { code: 'BID', name: 'BIDV' },
  { code: 'CTG', name: 'VietinBank' },
  { code: 'TPB', name: 'TPBank' },
  { code: 'STB', name: 'Sacombank' },
  { code: 'HDB', name: 'HDBank' },
];

export default function TransferPage() {
  const { type } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();

  const isInternal = type === 'internal';
  const title = isInternal ? 'Internal Transfer' : 'External Transfer';

  const [step, setStep] = useState(STEPS.FORM);

  const [bankCode, setBankCode] = useState(isInternal ? INTERNAL_BANK_CODE : '');
  const [receiverAccountNo, setReceiverAccountNo] = useState('');
  const [amountDisplay, setAmountDisplay] = useState('');
  const [description, setDescription] = useState('');
  const [pin, setPin] = useState('');

  const [receiverInfo, setReceiverInfo] = useState(null);
  const [inquiryLoading, setInquiryLoading] = useState(false);
  const [inquiryError, setInquiryError] = useState('');

  const [limitError, setLimitError] = useState('');
  const [limitLoading, setLimitLoading] = useState(false);

  const [transferResult, setTransferResult] = useState(null);
  const [transferError, setTransferError] = useState('');
  const [transferLoading, setTransferLoading] = useState(false);

  const currentBankCode = isInternal ? INTERNAL_BANK_CODE : bankCode;
  const rawAmount = parseAmountRaw(amountDisplay);

  const handleInquiry = async () => {
    if (!receiverAccountNo.trim() || !currentBankCode) return;
    setInquiryLoading(true);
    setInquiryError('');
    setReceiverInfo(null);
    try {
      const res = await transferApi.inquiryReceiver(receiverAccountNo.trim(), currentBankCode);
      const info = res.data.data;
      setReceiverInfo(info);
      const name = info.fullName || info.receiverName || '';
      setDescription(name ? `${name} transfer from VikkiBank` : 'Transfer from VikkiBank');
    } catch (err) {
      setInquiryError(err.response?.data?.message || 'Account not found');
    } finally {
      setInquiryLoading(false);
    }
  };

  const handleProceedToConfirm = async (e) => {
    e.preventDefault();
    if (!receiverInfo || !rawAmount) return;

    setLimitError('');
    setLimitLoading(true);
    try {
      const res = await limitApi.getLimitInfo(user.accountNo);
      const limits = res.data.data || [];
      const transferType = isInternal ? 'INTERNAL' : 'EXTERNAL';
      const lim = limits.find((l) => l.transferType === transferType);

      if (lim) {
        const amt = parseFloat(rawAmount);
        if (amt > lim.singleLimit) {
          setLimitError(`Exceeds single transaction limit: max ${formatCurrency(lim.singleLimit)}`);
          return;
        }
        if (lim.usedDaily + amt > lim.dailyLimit) {
          const remain = lim.dailyLimit - lim.usedDaily;
          setLimitError(`Exceeds daily limit: ${formatCurrency(remain)} remaining`);
          return;
        }
        if (lim.usedMonthly + amt > lim.monthlyLimit) {
          const remain = lim.monthlyLimit - lim.usedMonthly;
          setLimitError(`Exceeds monthly limit: ${formatCurrency(remain)} remaining`);
          return;
        }
      }

      setPin('');
      setTransferError('');
      setStep(STEPS.CONFIRM);
    } catch {
      setPin('');
      setTransferError('');
      setStep(STEPS.CONFIRM);
    } finally {
      setLimitLoading(false);
    }
  };

  const handleTransfer = async () => {
    if (pin.length !== 6) return;
    setTransferLoading(true);
    setTransferError('');
    try {
      const res = await transferApi.transfer({
        referenceId: `WEB-${Date.now()}`,
        senderAccountNo: user.accountNo,
        receiverAccountNo: receiverAccountNo.trim(),
        receiverName: receiverInfo.fullName || receiverInfo.receiverName || '',
        bankCode: currentBankCode,
        amount: parseFloat(rawAmount),
        description: description || `Transfer to ${receiverAccountNo}`,
        pin,
      });
      setTransferResult(res.data.data);
      setStep(STEPS.RESULT);
    } catch (err) {
      setTransferError(err.response?.data?.message || 'Transfer failed');
    } finally {
      setTransferLoading(false);
    }
  };

  const handleNewTransfer = () => {
    setStep(STEPS.FORM);
    setReceiverAccountNo('');
    setAmountDisplay('');
    setDescription('');
    setPin('');
    setReceiverInfo(null);
    setTransferResult(null);
    setTransferError('');
    setInquiryError('');
    setLimitError('');
    if (!isInternal) setBankCode('');
  };

  const resetInquiry = () => {
    setReceiverInfo(null);
    setInquiryError('');
    setLimitError('');
  };

  const demoReceivers = [
    { accountNo: '1000000001', name: 'Nguyen Van A' },
    { accountNo: '1000000002', name: 'Tran Van B' },
    { accountNo: '1000000003', name: 'Le Thi C' },
  ].filter((d) => d.accountNo !== user?.accountNo);

  const bankLabel = isInternal
    ? 'VikkiBank'
    : EXTERNAL_BANKS.find((b) => b.code === bankCode)?.name || bankCode;

  return (
    <div className="page">
      <div className="page-header">
        <div className="page-header-left">
          <button className="btn btn-outline btn-sm" onClick={() => navigate('/transfer')}>
            &#x2190; Back
          </button>
          <h2>{title}</h2>
        </div>
      </div>

      {/* Step indicator */}
      <div className="steps-indicator">
        <div className={`step ${step >= STEPS.FORM ? 'active' : ''}`}>
          <span className="step-num">1</span>
          <span className="step-label">Details</span>
        </div>
        <div className="step-line" />
        <div className={`step ${step >= STEPS.CONFIRM ? 'active' : ''}`}>
          <span className="step-num">2</span>
          <span className="step-label">Confirm</span>
        </div>
        <div className="step-line" />
        <div className={`step ${step >= STEPS.RESULT ? 'active' : ''}`}>
          <span className="step-num">3</span>
          <span className="step-label">Result</span>
        </div>
      </div>

      {/* Step 1: Form */}
      {step === STEPS.FORM && (
        <div className="transfer-card">
          <form onSubmit={handleProceedToConfirm}>
            <div className="form-section">
              <h3>From Account</h3>
              <div className="from-account-box">
                <span className="mono">{user?.accountNo}</span>
                <span className="from-bank-tag">VikkiBank</span>
              </div>
            </div>

            {!isInternal && (
              <div className="form-section">
                <h3>Receiving Bank</h3>
                <div className="form-group">
                  <label htmlFor="bankCode">Bank</label>
                  <select
                    id="bankCode"
                    value={bankCode}
                    onChange={(e) => {
                      setBankCode(e.target.value);
                      resetInquiry();
                    }}
                    required
                  >
                    <option value="">-- Select bank --</option>
                    {EXTERNAL_BANKS.map((b) => (
                      <option key={b.code} value={b.code}>
                        {b.name} ({b.code})
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            )}

            <div className="form-section">
              <h3>To Account</h3>
              <div className="form-row">
                <div className="form-group flex-1">
                  <label htmlFor="receiverAccountNo">Receiver Account Number</label>
                  <div className="input-with-btn">
                    <input
                      id="receiverAccountNo"
                      type="text"
                      value={receiverAccountNo}
                      onChange={(e) => {
                        setReceiverAccountNo(e.target.value);
                        resetInquiry();
                      }}
                      placeholder="Enter account number"
                      required
                    />
                    <button
                      type="button"
                      className="btn btn-secondary"
                      onClick={handleInquiry}
                      disabled={inquiryLoading || !receiverAccountNo.trim() || (!isInternal && !bankCode)}
                    >
                      {inquiryLoading ? 'Searching...' : 'Inquiry'}
                    </button>
                  </div>
                </div>
              </div>

              {isInternal && (
                <div className="demo-chips small">
                  {demoReceivers.map((d) => (
                    <button
                      key={d.accountNo}
                      type="button"
                      className="chip"
                      onClick={() => {
                        setReceiverAccountNo(d.accountNo);
                        resetInquiry();
                      }}
                    >
                      {d.accountNo} - {d.name}
                    </button>
                  ))}
                </div>
              )}

              {inquiryError && <div className="alert alert-error">{inquiryError}</div>}

              {receiverInfo && (
                <div className="receiver-verified">
                  <span className="verified-icon">&#x2705;</span>
                  <span className="verified-name">
                    {receiverInfo.fullName || receiverInfo.receiverName || 'Verified'}
                  </span>
                </div>
              )}
            </div>

            <div className="form-section">
              <h3>Transfer Details</h3>
              <div className="form-group">
                <label htmlFor="amount">Amount (VND)</label>
                <input
                  id="amount"
                  type="text"
                  inputMode="numeric"
                  value={amountDisplay}
                  onChange={(e) => setAmountDisplay(formatAmountDisplay(e.target.value))}
                  placeholder="Enter amount"
                  required
                />
              </div>
              <div className="form-group">
                <label htmlFor="description">Description</label>
                <input
                  id="description"
                  type="text"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Transfer description (auto-filled after inquiry)"
                />
              </div>
            </div>

            {limitError && <div className="alert alert-error">{limitError}</div>}

            <button
              type="submit"
              className="btn btn-primary btn-block"
              disabled={!receiverInfo || !rawAmount || limitLoading}
            >
              {limitLoading ? 'Checking limits...' : 'Continue'}
            </button>
          </form>
        </div>
      )}

      {/* Step 2: Confirm + PIN */}
      {step === STEPS.CONFIRM && (
        <div className="transfer-card">
          <div className="confirm-summary">
            <h3>Transfer Summary</h3>
            <div className="info-rows">
              <div className="info-row">
                <span className="label">Type</span>
                <span className={`badge ${isInternal ? 'badge-info' : 'badge-warning'}`}>
                  {isInternal ? 'Internal' : 'External'}
                </span>
              </div>
              <div className="info-row">
                <span className="label">From</span>
                <span className="value mono">{user?.accountNo}</span>
              </div>
              {!isInternal && (
                <div className="info-row">
                  <span className="label">Bank</span>
                  <span className="value">{bankLabel}</span>
                </div>
              )}
              <div className="info-row">
                <span className="label">To</span>
                <span className="value mono">{receiverAccountNo}</span>
              </div>
              <div className="info-row">
                <span className="label">Receiver</span>
                <span className="value">
                  {receiverInfo?.fullName || receiverInfo?.receiverName || '---'}
                </span>
              </div>
              <div className="info-row highlight">
                <span className="label">Amount</span>
                <span className="value amount-lg">{formatCurrency(parseFloat(rawAmount))}</span>
              </div>
              <div className="info-row">
                <span className="label">Description</span>
                <span className="value">{description || '---'}</span>
              </div>
            </div>
          </div>

          <div className="form-group" style={{ marginTop: '1.5rem' }}>
            <label htmlFor="pin">PIN (6 digits)</label>
            <input
              id="pin"
              type="password"
              value={pin}
              onChange={(e) => setPin(e.target.value.replace(/\D/g, '').slice(0, 6))}
              placeholder="Enter your PIN"
              maxLength={6}
              inputMode="numeric"
              autoFocus
            />
          </div>

          {transferError && <div className="alert alert-error">{transferError}</div>}

          <div className="btn-group">
            <button
              className="btn btn-outline"
              onClick={() => { setStep(STEPS.FORM); setTransferError(''); setPin(''); }}
              disabled={transferLoading}
            >
              Back
            </button>
            <button
              className="btn btn-primary"
              onClick={handleTransfer}
              disabled={transferLoading || pin.length !== 6}
            >
              {transferLoading ? 'Processing...' : 'Confirm Transfer'}
            </button>
          </div>
        </div>
      )}

      {/* Step 3: Result */}
      {step === STEPS.RESULT && (() => {
        const status = transferResult?.status || 'COMPLETED';
        const isSuccess = status === 'COMPLETED';
        const isPending = status === 'PENDING';
        return (
          <div className="transfer-card">
            <div className={`result-box ${isSuccess ? 'success' : isPending ? 'pending' : 'failed'}`}>
              <div className="result-icon">{isSuccess ? '✅' : isPending ? '⏳' : '❌'}</div>
              <h3>{isSuccess ? 'Transfer Successful' : isPending ? 'Transfer Pending' : 'Transfer Failed'}</h3>
              <p className="result-amount">{formatCurrency(parseFloat(rawAmount))}</p>
              <p className="result-desc">
                To: {receiverInfo?.fullName || receiverInfo?.receiverName} ({receiverAccountNo})
                {!isInternal && <span> - {bankLabel}</span>}
              </p>
            </div>

            {transferResult && (
              <div className="info-rows" style={{ marginTop: '1.5rem' }}>
                {transferResult.referenceId && (
                  <div className="info-row">
                    <span className="label">Reference ID</span>
                    <span className="value mono">{transferResult.referenceId}</span>
                  </div>
                )}
                {transferResult.txId && (
                  <div className="info-row">
                    <span className="label">Transaction ID</span>
                    <span className="value mono">{transferResult.txId}</span>
                  </div>
                )}
                {transferResult.napasRef && (
                  <div className="info-row">
                    <span className="label">NAPAS Ref</span>
                    <span className="value mono">{transferResult.napasRef}</span>
                  </div>
                )}
                {transferResult.status && (
                  <div className="info-row">
                    <span className="label">Status</span>
                    <span className={`badge ${isSuccess ? 'badge-success' : isPending ? 'badge-warning' : 'badge-error'}`}>
                      {status}
                    </span>
                  </div>
                )}
              </div>
            )}

            <div className="btn-group" style={{ marginTop: '1.5rem' }}>
              <button className="btn btn-outline" onClick={() => navigate('/transfer')}>
                Back to Menu
              </button>
              <button className="btn btn-primary" onClick={handleNewTransfer}>
                New Transfer
              </button>
            </div>
          </div>
        );
      })()}
    </div>
  );
}
