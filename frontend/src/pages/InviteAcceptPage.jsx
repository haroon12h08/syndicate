import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import * as invitationsApi from '../api/invitations';
import { humanize } from '../constants';

export default function InviteAcceptPage() {
  const { token } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [preview, setPreview] = useState(null);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(null);

  useEffect(() => {
    invitationsApi.previewInvitation(token)
      .then(setPreview)
      .catch((err) => setError(err.message));
  }, [token]);

  async function handleAccept() {
    setSubmitting(true);
    setError(null);
    try {
      await invitationsApi.acceptInvitation(token);
      setDone('accepted');
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleReject() {
    setSubmitting(true);
    setError(null);
    try {
      await invitationsApi.rejectInvitation(token);
      setDone('rejected');
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  if (error && !preview) {
    return (
      <div className="auth-page">
        <div className="auth-form">
          <h1>Invitation</h1>
          <div className="error-banner">{error}</div>
        </div>
      </div>
    );
  }

  if (!preview) {
    return <div className="page-loading">Loading invitation...</div>;
  }

  if (done) {
    return (
      <div className="auth-page">
        <div className="auth-form">
          <h1>{done === 'accepted' ? 'Invitation accepted' : 'Invitation declined'}</h1>
          <p className="hint">
            {done === 'accepted'
              ? 'You now have access to the workspace.'
              : 'You have declined this invitation.'}
          </p>
          <button onClick={() => navigate('/home')}>Go to dashboard</button>
        </div>
      </div>
    );
  }

  const isTransaction = preview.kind.startsWith('TRANSACTION');
  const isOrgLevel = preview.kind === 'TRANSACTION_ORGANIZATION';

  return (
    <div className="auth-page">
      <div className="auth-form" style={{ width: 420 }}>
        <div className="eyebrow">You're invited</div>
        <h1>{isTransaction ? preview.transactionName : preview.organizationName}</h1>

        <div className="detail-grid" style={{ margin: 0 }}>
          <div><strong>Organization</strong><span>{preview.organizationName}</span></div>
          <div><strong>Role offered</strong><span>{humanize(preview.role)}</span></div>
          {preview.workstreamLabel && (
            <div><strong>Workstream</strong><span>{humanize(preview.workstreamLabel)}</span></div>
          )}
          <div><strong>Invited by</strong><span>{preview.inviterName}</span></div>
        </div>

        {isOrgLevel && (
          <p className="hint">
            This invites your organization as a whole. Only an OWNER or ADMIN of{' '}
            {preview.organizationName} can accept — once accepted, they can assign specific
            team members to the transaction.
          </p>
        )}

        {preview.expired || preview.status !== 'PENDING' ? (
          <div className="error-banner">
            This invitation is {preview.expired ? 'expired' : humanize(preview.status).toLowerCase()}.
          </div>
        ) : !user ? (
          <>
            <p className="hint">Log in or create an account to respond to this invitation.</p>
            <div className="form-actions">
              <button onClick={() => navigate('/login', { state: { from: `/invite/${token}` } })}>Log in</button>
              <button className="secondary" onClick={() => navigate('/register', { state: { from: `/invite/${token}` } })}>
                Create account
              </button>
            </div>
          </>
        ) : (
          <>
            {error && <div className="error-banner">{error}</div>}
            <div className="form-actions">
              <button disabled={submitting} onClick={handleAccept}>
                {submitting ? 'Joining...' : 'Accept & Join Transaction Workspace'}
              </button>
              <button className="secondary" disabled={submitting} onClick={handleReject}>Decline</button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
