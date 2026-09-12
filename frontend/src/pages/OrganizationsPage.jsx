import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as organizationsApi from '../api/organizations';
import { humanize } from '../constants';

export default function OrganizationsPage() {
  const [organizations, setOrganizations] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    organizationsApi.listOrganizations()
      .then(setOrganizations)
      .catch((err) => setError(err.message));
  }, []);

  return (
    <div className="page">
      <div className="page-header"><h1>Organizations</h1></div>
      <p className="hint">Firms you belong to. Invite colleagues from an organization's page.</p>
      {error && <div className="error-banner">{error}</div>}

      <table className="data-table">
        <thead>
          <tr><th>Name</th><th>Type</th><th></th></tr>
        </thead>
        <tbody>
          {organizations.map((o) => (
            <tr key={o.id}>
              <td>{o.name}</td>
              <td>{humanize(o.type)}</td>
              <td><Link to={`/organizations/${o.id}`}>Open</Link></td>
            </tr>
          ))}
          {organizations.length === 0 && (
            <tr><td colSpan={3} className="hint">You are not a member of any organization yet.</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
