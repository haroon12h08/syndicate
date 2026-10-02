import { useEffect, useState } from 'react';
import { humanize } from '../constants';
import * as reviewsApi from '../api/reviews';

/**
 * Recording that a person looked at this exact version and what they concluded. A later
 * correction does not erase the review; it simply stops covering the value as it now stands.
 */
export default function FactReviews({ factId, factVersion, evidence = [], onReviewed }) {
  const [reviews, setReviews] = useState([]);
  const [decision, setDecision] = useState('APPROVED_FOR_USE');
  const [comments, setComments] = useState('');
  const [considered, setConsidered] = useState([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  function reload() {
    reviewsApi.listFactReviews(factId).then(setReviews).catch(() => setReviews([]));
  }

  useEffect(reload, [factId]);

  async function submit(e) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await reviewsApi.reviewFact(factId, { decision, comments: comments.trim() || null, evidenceConsideredIds: considered });
      setComments('');
      setConsidered([]);
      reload();
      if (onReviewed) onReviewed();
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="fact-reviews">
      <h2>Review</h2>
      <p className="hint">
        A review records that someone checked this value against its evidence. It is tied to
        version {factVersion}; if the value is corrected, the review no longer covers it.
      </p>

      {reviews.length > 0 && (
        <table className="data-table">
          <thead><tr><th>Decision</th><th>By</th><th>When</th><th>Covers this version</th></tr></thead>
          <tbody>
            {reviews.map((r) => (
              <tr key={r.id}>
                <td>{humanize(r.decision)}{r.comments && <div className="hint">{r.comments}</div>}</td>
                <td>{r.reviewer.fullName} <span className="hint">{humanize(r.reviewerRole)}</span></td>
                <td>{new Date(r.reviewedAt).toLocaleString()}</td>
                <td>{r.current
                  ? <span className="badge badge-complete">Yes</span>
                  : <span className="badge badge-pending">Superseded</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <form className="card-form" onSubmit={submit}>
        <label>
          Decision
          <select value={decision} onChange={(e) => setDecision(e.target.value)}>
            <option value="APPROVED_FOR_USE">This value is right and supported</option>
            <option value="CHANGES_REQUESTED">Something needs to change</option>
            <option value="REJECTED">This value should not be used</option>
          </select>
        </label>
        <label>
          Comments
          <textarea rows={2} value={comments} onChange={(e) => setComments(e.target.value)}
            placeholder={decision === 'APPROVED_FOR_USE' ? 'Optional' : 'What needs to change, and why'} />
        </label>
        {evidence.length > 0 && (
          <fieldset>
            <legend>Evidence considered</legend>
            {evidence.map((e) => (
              <label key={e.id} className="checkbox-label">
                <input
                  type="checkbox"
                  checked={considered.includes(e.id)}
                  onChange={() => setConsidered((ids) => ids.includes(e.id)
                    ? ids.filter((x) => x !== e.id)
                    : [...ids, e.id])}
                />
                {e.fileName}
              </label>
            ))}
          </fieldset>
        )}
        {error && <div className="error-banner">{error}</div>}
        <button type="submit" disabled={saving}>{saving ? 'Recording…' : 'Record review'}</button>
      </form>
    </section>
  );
}
