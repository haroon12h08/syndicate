import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as invitationsApi from '../api/invitations';
import { humanize } from '../constants';

export default function InvitationsInboxPage() {
  const [invitations, setInvitations] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    invitationsApi.listMyInvitations()
      .then(setInvitations)
      .catch((err) => setError(err.message));
  }, []);

  return (
    <div className="page">
      <h1>Invitations</h1>
      {error && <div className="error-banner">{error}</div>}
      <ul className="entity-list">
        {invitations.map((inv) => (
          <li key={inv.id}>
            <Link to={`/invite/${inv.token}`}>
              {inv.organizationInvite || !inv.inviteeEmail
                ? `${inv.organizationName} → ${humanize(inv.role)}`
                : `${inv.transactionName || inv.organizationName} → ${humanize(inv.role)}`}
            </Link>
            <span className="hint"> — invited by {inv.inviter.fullName}</span>
          </li>
        ))}
        {invitations.length === 0 && <li className="hint">No pending invitations.</li>}
      </ul>
    </div>
  );
}
