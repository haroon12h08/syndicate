import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as onboardingApi from '../api/onboarding';

/**
 * The only thing Syndicate needs to open a deal is whose issue it is. The issue type, the lead
 * organisation, the opening role and the diligence areas follow from that, so it does not ask.
 */
export default function StartTransactionCard({ compact = false }) {
  const navigate = useNavigate();
  const [companyName, setCompanyName] = useState('');
  const [cin, setCin] = useState('');
  const [showCin, setShowCin] = useState(false);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState(null);

  async function submit(e) {
    e.preventDefault();
    setStarting(true);
    setError(null);
    try {
      const transaction = await onboardingApi.startTransaction({ companyName: companyName.trim(), cin: cin.trim() || null });
      navigate(`/transactions/${transaction.id}`);
    } catch (err) {
      setError(err.message);
      setStarting(false);
    }
  }

  return (
    <section className="start-card">
      {!compact && <h2>Start an IPO</h2>}
      <form className="inline-form" onSubmit={submit}>
        <input
          value={companyName}
          onChange={(e) => setCompanyName(e.target.value)}
          placeholder="Name of the company going public"
          aria-label="Company name"
          required
        />
        {showCin && (
          <input value={cin} onChange={(e) => setCin(e.target.value)} placeholder="CIN (optional)" aria-label="CIN" />
        )}
        <button type="submit" disabled={starting || !companyName.trim()}>
          {starting ? 'Opening…' : 'Start'}
        </button>
      </form>
      {!showCin && (
        <button type="button" className="link-button" onClick={() => setShowCin(true)}>Add the CIN now</button>
      )}
      {error && <div className="error-banner">{error}</div>}
      {!compact && (
        <p className="hint">
          Opens the transaction with its capital structure, financial, legal and regulatory diligence
          areas ready. Everything else is decided as you go.
        </p>
      )}
    </section>
  );
}
