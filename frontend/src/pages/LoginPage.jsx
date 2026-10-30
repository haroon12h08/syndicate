import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const { login, completeMfaLogin } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [mfaRequired, setMfaRequired] = useState(false);
  const [mfaToken, setMfaToken] = useState(null);
  const [mfaCode, setMfaCode] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleLoginSubmit(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const response = await login(email, password);
      if (response && response.mfaRequired) {
        setMfaRequired(true);
        setMfaToken(response.mfaToken);
      } else {
        navigate('/home');
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleMfaSubmit(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await completeMfaLogin(mfaToken, mfaCode);
      navigate('/home');
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  function handleCancelMfa() {
    setMfaRequired(false);
    setMfaToken(null);
    setMfaCode('');
    setError(null);
  }

  if (mfaRequired) {
    return (
      <div className="auth-page">
        <form className="auth-form" onSubmit={handleMfaSubmit}>
          <h1>Two-Factor Verification</h1>
          <p style={{ color: '#64748b', fontSize: '0.9rem', marginBottom: '1rem' }}>
            Enter the 6-digit verification code from your authenticator app (Google Authenticator, 1Password, etc.)
          </p>
          {error && <div className="error-banner">{error}</div>}
          <label>
            Authentication Code
            <input
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              maxLength={6}
              autoFocus
              placeholder="000000"
              style={{ letterSpacing: '0.25em', fontSize: '1.25rem', textAlign: 'center' }}
              value={mfaCode}
              onChange={(e) => setMfaCode(e.target.value)}
              required
            />
          </label>
          <button type="submit" disabled={submitting || mfaCode.length < 6}>
            {submitting ? 'Verifying...' : 'Verify & Sign In'}
          </button>
          <button
            type="button"
            className="secondary-btn"
            style={{ marginTop: '0.5rem' }}
            onClick={handleCancelMfa}
          >
            Back to Log in
          </button>
        </form>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <form className="auth-form" onSubmit={handleLoginSubmit}>
        <h1>Log in</h1>
        {error && <div className="error-banner">{error}</div>}
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        <button type="submit" disabled={submitting}>{submitting ? 'Logging in...' : 'Log in'}</button>
        <p>No account? <Link to="/register">Register</Link></p>
      </form>
    </div>
  );
}
