import { useState } from 'react';
import * as authApi from '../api/auth';
import { useAuth } from '../context/AuthContext';

export default function UserSettingsModal({ isOpen, onClose }) {
  const { user, setUser } = useAuth();
  const [setupData, setSetupData] = useState(null);
  const [verifyCode, setVerifyCode] = useState('');
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [loading, setLoading] = useState(false);
  const [showDisableConfirm, setShowDisableConfirm] = useState(false);
  const [disableCode, setDisableCode] = useState('');

  if (!isOpen) return null;

  async function handleStartSetup() {
    setError(null);
    setLoading(true);
    try {
      const data = await authApi.setupMfa();
      setSetupData(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleConfirmEnable(e) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const updated = await authApi.enableMfa(parseInt(verifyCode, 10));
      setUser(updated);
      setSuccess('Two-factor authentication successfully enabled!');
      setSetupData(null);
      setVerifyCode('');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleConfirmDisable(e) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const updated = await authApi.disableMfa(parseInt(disableCode, 10));
      setUser(updated);
      setSuccess('Two-factor authentication has been disabled.');
      setShowDisableConfirm(false);
      setDisableCode('');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card" style={{ maxWidth: 520 }} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
          <h2 style={{ margin: 0, fontSize: '1.25rem' }}>Security & Account Settings</h2>
          <button className="secondary" style={{ padding: '0.25rem 0.5rem' }} onClick={onClose}>✕</button>
        </div>

        {error && <div className="error-banner" style={{ marginBottom: '1rem' }}>{error}</div>}
        {success && <div className="success-banner" style={{ marginBottom: '1rem', background: '#ecfdf5', color: '#065f46', padding: '0.75rem', borderRadius: 4, border: '1px solid #a7f3d0' }}>{success}</div>}

        <div style={{ marginBottom: '1.5rem', background: '#f8fafc', padding: '1rem', borderRadius: 6, border: '1px solid #e2e8f0' }}>
          <div><strong>User:</strong> {user?.fullName}</div>
          <div style={{ marginTop: 4 }}><strong>Email:</strong> {user?.email}</div>
          <div style={{ marginTop: 4, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <strong>Two-Factor Auth (MFA):</strong>
            {user?.mfaEnabled ? (
              <span style={{ background: '#dcfce7', color: '#15803d', fontSize: '0.8rem', padding: '2px 8px', borderRadius: 9999, fontWeight: 600 }}>
                Enabled (TOTP RFC 6238)
              </span>
            ) : (
              <span style={{ background: '#f1f5f9', color: '#64748b', fontSize: '0.8rem', padding: '2px 8px', borderRadius: 9999 }}>
                Not Enabled
              </span>
            )}
          </div>
        </div>

        {!user?.mfaEnabled && !setupData && (
          <div>
            <p style={{ color: '#475569', fontSize: '0.9rem', marginBottom: '1rem' }}>
              Protect your privileged deal actions, approvals, and verified fact certifications by enabling RFC 6238 TOTP two-factor authentication.
            </p>
            <button onClick={handleStartSetup} disabled={loading}>
              {loading ? 'Initializing...' : 'Setup Two-Factor Authentication'}
            </button>
          </div>
        )}

        {setupData && (
          <form onSubmit={handleConfirmEnable} style={{ borderTop: '1px solid #e2e8f0', paddingTop: '1rem' }}>
            <h3 style={{ fontSize: '1rem', marginBottom: '0.5rem' }}>Step 1: Scan or enter secret in Authenticator App</h3>
            <p style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '0.75rem' }}>
              Add this account to Google Authenticator, 1Password, or any standard TOTP app using this setup key:
            </p>
            <div style={{ background: '#0f172a', color: '#38bdf8', padding: '0.75rem', borderRadius: 4, fontFamily: 'monospace', fontSize: '1.1rem', letterSpacing: '0.1em', textAlign: 'center', marginBottom: '1rem' }}>
              {setupData.secret}
            </div>
            <div style={{ fontSize: '0.75rem', color: '#94a3b8', wordBreak: 'break-all', marginBottom: '1rem' }}>
              URI: {setupData.qrCodeUri}
            </div>

            <h3 style={{ fontSize: '1rem', marginBottom: '0.5rem' }}>Step 2: Enter 6-digit code to verify</h3>
            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
              <input
                type="text"
                maxLength={6}
                pattern="[0-9]*"
                placeholder="000000"
                style={{ width: 140, fontSize: '1.1rem', letterSpacing: '0.2em', textAlign: 'center' }}
                value={verifyCode}
                onChange={(e) => setVerifyCode(e.target.value)}
                required
              />
              <button type="submit" disabled={loading || verifyCode.length < 6}>
                {loading ? 'Verifying...' : 'Enable Two-Factor'}
              </button>
              <button type="button" className="secondary" onClick={() => setSetupData(null)}>
                Cancel
              </button>
            </div>
          </form>
        )}

        {user?.mfaEnabled && !showDisableConfirm && (
          <div>
            <p style={{ color: '#475569', fontSize: '0.9rem', marginBottom: '1rem' }}>
              Two-factor authentication is active. You will be prompted for your 6-digit code upon login and when performing sensitive actions.
            </p>
            <button className="secondary" onClick={() => setShowDisableConfirm(true)}>
              Disable Two-Factor Authentication
            </button>
          </div>
        )}

        {showDisableConfirm && (
          <form onSubmit={handleConfirmDisable} style={{ borderTop: '1px solid #fee2e2', paddingTop: '1rem', background: '#fff5f5', padding: '1rem', borderRadius: 6 }}>
            <h3 style={{ color: '#b91c1c', fontSize: '0.95rem', margin: '0 0 0.5rem 0' }}>Confirm MFA Deactivation</h3>
            <p style={{ fontSize: '0.85rem', color: '#7f1d1d', marginBottom: '0.75rem' }}>
              Enter your current 6-digit authenticator code to confirm turning off two-factor authentication:
            </p>
            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
              <input
                type="text"
                maxLength={6}
                pattern="[0-9]*"
                placeholder="000000"
                style={{ width: 140, fontSize: '1.1rem', letterSpacing: '0.2em', textAlign: 'center' }}
                value={disableCode}
                onChange={(e) => setDisableCode(e.target.value)}
                required
              />
              <button type="submit" style={{ background: '#dc2626' }} disabled={loading || disableCode.length < 6}>
                {loading ? 'Deactivating...' : 'Confirm Disable'}
              </button>
              <button type="button" className="secondary" onClick={() => setShowDisableConfirm(false)}>
                Cancel
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
