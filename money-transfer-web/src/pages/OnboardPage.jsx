import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as onboardApi from '../api/onboardApi';

const STEPS = { FORM: 0, PROCESSING: 1, RESULT: 2 };

const ID_TYPES = [
  { value: 'CCCD', label: 'CCCD (Citizen ID)' },
  { value: 'CMND', label: 'CMND (Old ID)' },
  { value: 'PASSPORT', label: 'Passport' },
];

export default function OnboardPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(STEPS.FORM);

  // Form fields
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [idNumber, setIdNumber] = useState('');
  const [idType, setIdType] = useState('CCCD');
  const [dob, setDob] = useState('');
  const [address, setAddress] = useState('');

  // Processing state
  const [currentTask, setCurrentTask] = useState('');
  const [tasks, setTasks] = useState([]);
  const [error, setError] = useState('');

  // Result
  const [result, setResult] = useState(null);

  const addTask = (label, status) => {
    setTasks((prev) => {
      const existing = prev.findIndex((t) => t.label === label);
      if (existing >= 0) {
        const updated = [...prev];
        updated[existing] = { label, status };
        return updated;
      }
      return [...prev, { label, status }];
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }
    setError('');
    setStep(STEPS.PROCESSING);
    setTasks([]);

    try {
      // Step 1: Register
      setCurrentTask('Creating user account...');
      addTask('Register user', 'loading');
      const regRes = await onboardApi.register(password, phone, email);
      const userId = regRes.data.data.userId;
      addTask('Register user', 'done');

      // Step 2: Create bank account
      setCurrentTask('Opening bank account...');
      addTask('Open bank account', 'loading');
      const accRes = await onboardApi.createAccount(userId, fullName, phone, email, dob || null, address || null);
      const accountNo = accRes.data.data.accountNo;
      addTask('Open bank account', 'done');

      // Step 3: Update username to accountNo
      setCurrentTask('Setting up login credentials...');
      addTask('Setup credentials', 'loading');
      await onboardApi.updateUsername(userId, accountNo);
      addTask('Setup credentials', 'done');

      // Step 4: KYC verify
      setCurrentTask('Verifying identity (KYC)...');
      addTask('KYC verification', 'loading');
      await onboardApi.kycVerify(userId, accountNo, fullName, idNumber, idType);
      addTask('KYC verification', 'done');

      // Step 5: Init limits
      setCurrentTask('Setting transfer limits...');
      addTask('Transfer limits', 'loading');
      await onboardApi.initLimits(accountNo);
      addTask('Transfer limits', 'done');

      // Step 6: Credit welcome bonus
      setCurrentTask('Crediting welcome bonus...');
      addTask('Welcome bonus', 'loading');
      await onboardApi.creditWelcomeBonus(accountNo);
      addTask('Welcome bonus', 'done');

      setCurrentTask('');
      setResult({ accountNo, fullName });
      setStep(STEPS.RESULT);
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Registration failed';
      setError(msg);
      // Mark current loading task as failed
      setTasks((prev) =>
        prev.map((t) => (t.status === 'loading' ? { ...t, status: 'failed' } : t))
      );
    }
  };

  return (
    <div className="login-container">
      <div className="onboard-card">
        {/* Header */}
        <div className="login-header">
          <div className="login-logo">$</div>
          <h1>Open Account</h1>
          <p>Create your MoneyTransfer account in minutes</p>
        </div>

        {/* Step 1: Form */}
        {step === STEPS.FORM && (
          <form onSubmit={handleSubmit}>
            {error && <div className="alert alert-error">{error}</div>}

            <div className="onboard-section">
              <h3>Personal Information</h3>
              <div className="form-group">
                <label>Full Name</label>
                <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)}
                  placeholder="e.g. Nguyen Van A" required />
              </div>
              <div className="form-row">
                <div className="form-group flex-1">
                  <label>Phone</label>
                  <input type="tel" value={phone} onChange={(e) => setPhone(e.target.value)}
                    placeholder="0901234567" required />
                </div>
                <div className="form-group flex-1">
                  <label>Email</label>
                  <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                    placeholder="you@email.com" />
                </div>
              </div>
              <div className="form-row">
                <div className="form-group flex-1">
                  <label>Date of Birth</label>
                  <input type="date" value={dob} onChange={(e) => setDob(e.target.value)} />
                </div>
                <div className="form-group flex-1">
                  <label>Address</label>
                  <input type="text" value={address} onChange={(e) => setAddress(e.target.value)}
                    placeholder="Optional" />
                </div>
              </div>
            </div>

            <div className="onboard-section">
              <h3>Identity Verification</h3>
              <div className="form-row">
                <div className="form-group flex-1">
                  <label>ID Type</label>
                  <select value={idType} onChange={(e) => setIdType(e.target.value)} required>
                    {ID_TYPES.map((t) => (
                      <option key={t.value} value={t.value}>{t.label}</option>
                    ))}
                  </select>
                </div>
                <div className="form-group flex-1">
                  <label>ID Number</label>
                  <input type="text" value={idNumber} onChange={(e) => setIdNumber(e.target.value)}
                    placeholder="012345678901" required />
                </div>
              </div>
            </div>

            <div className="onboard-section">
              <h3>Login Credentials</h3>
              <div className="form-group">
                <label>Password</label>
                <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                  placeholder="Min 6 characters" minLength={6} required />
              </div>
              <div className="form-group">
                <label>Confirm Password</label>
                <input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Re-enter password" minLength={6} required />
              </div>
            </div>

            <button type="submit" className="btn btn-primary btn-block">
              Create Account
            </button>

            <div className="onboard-login-link">
              Already have an account?{' '}
              <a href="#" onClick={(e) => { e.preventDefault(); navigate('/login'); }}>Login here</a>
            </div>
          </form>
        )}

        {/* Step 2: Processing */}
        {step === STEPS.PROCESSING && (
          <div className="onboard-processing">
            <div className="spinner" style={{ margin: '0 auto 1.5rem' }} />
            <p className="onboard-task-label">{currentTask}</p>

            <div className="onboard-tasks">
              {tasks.map((t, i) => (
                <div className={`onboard-task-item ${t.status}`} key={i}>
                  <span className="onboard-task-icon">
                    {t.status === 'done' ? '✅' : t.status === 'failed' ? '❌' : '⏳'}
                  </span>
                  <span>{t.label}</span>
                </div>
              ))}
            </div>

            {error && (
              <div style={{ marginTop: '1.5rem' }}>
                <div className="alert alert-error">{error}</div>
                <button className="btn btn-secondary btn-block" onClick={() => { setStep(STEPS.FORM); setError(''); }}>
                  Try Again
                </button>
              </div>
            )}
          </div>
        )}

        {/* Step 3: Result */}
        {step === STEPS.RESULT && result && (
          <div className="onboard-result">
            <div className="result-box success">
              <div className="result-icon">🎉</div>
              <h3>Account Created!</h3>
              <p className="result-desc">Welcome to MoneyTransfer, {result.fullName}</p>
            </div>

            <div className="info-rows" style={{ marginTop: '1.5rem' }}>
              <div className="info-row highlight">
                <span className="label">Account Number</span>
                <span className="value mono" style={{ fontSize: '1.1rem', fontWeight: 700 }}>{result.accountNo}</span>
              </div>
              <div className="info-row">
                <span className="label">Welcome Bonus</span>
                <span className="value" style={{ color: 'var(--success)', fontWeight: 600 }}>+10,000,000 VND</span>
              </div>
              <div className="info-row">
                <span className="label">KYC Status</span>
                <span className="badge badge-success">VERIFIED</span>
              </div>
              <div className="info-row">
                <span className="label">Transfer Limits</span>
                <span className="badge badge-info">TIER 1</span>
              </div>
            </div>

            <div className="onboard-note">
              Use your <strong>account number</strong> and <strong>password</strong> to login.
            </div>

            <button className="btn btn-primary btn-block" onClick={() => navigate('/login')}>
              Login Now
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
