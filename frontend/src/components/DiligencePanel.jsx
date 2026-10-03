import { useState } from 'react';
import { humanize } from '../constants';

const STATUS_BADGE = {
  OPEN: 'badge badge-pending',
  ANSWERED: 'badge badge-processing',
  ACCEPTED: 'badge badge-complete',
  NOT_APPLICABLE: 'badge badge-not_applicable',
};

function AnswerForm({ question, facts, evidence, onAnswer }) {
  const [answer, setAnswer] = useState(question.answer || '');
  const [factIds, setFactIds] = useState(question.supportingFactIds || []);
  const [evidenceIds, setEvidenceIds] = useState(question.supportingEvidenceIds || []);
  const [notApplicable, setNotApplicable] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  const relevantFacts = facts.filter((f) => f.status !== 'SUPERSEDED' && f.status !== 'REJECTED');
  const toggle = (setter) => (id) => setter((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]));

  async function submit(e) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await onAnswer(question.id, notApplicable.trim()
        ? { notApplicableReason: notApplicable.trim() }
        : { answer, factIds, evidenceIds });
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="card-form" onSubmit={submit}>
      <label>
        Answer
        <textarea rows={3} value={answer} onChange={(e) => setAnswer(e.target.value)}
          placeholder="What the team established, in the words you would use to a reviewer" />
      </label>
      {relevantFacts.length > 0 && (
        <fieldset>
          <legend>Facts this rests on</legend>
          {relevantFacts.slice(0, 12).map((f) => (
            <label key={f.id} className="checkbox-label">
              <input type="checkbox" checked={factIds.includes(f.id)} onChange={() => toggle(setFactIds)(f.id)} />
              {f.label}: {f.value}{f.unit ? ` ${f.unit}` : ''}
            </label>
          ))}
        </fieldset>
      )}
      {evidence.length > 0 && (
        <fieldset>
          <legend>Documents this rests on</legend>
          {evidence.slice(0, 12).map((e) => (
            <label key={e.id} className="checkbox-label">
              <input type="checkbox" checked={evidenceIds.includes(e.id)} onChange={() => toggle(setEvidenceIds)(e.id)} />
              {e.fileName}
            </label>
          ))}
        </fieldset>
      )}
      <label>
        Or say why it does not apply
        <input value={notApplicable} onChange={(e) => setNotApplicable(e.target.value)}
          placeholder="e.g. the company has never raised equity before this issue" />
      </label>
      {error && <div className="error-banner">{error}</div>}
      <button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Record answer'}</button>
    </form>
  );
}

/**
 * Spec §9: the questions a diligence team already works through, kept as state so the answers,
 * their evidence and who accepted them survive the transaction.
 */
export default function DiligencePanel({ questions, facts = [], evidence = [], currentUserId, onAnswer, onAccept }) {
  const [openId, setOpenId] = useState(null);
  const areas = [...new Set(questions.map((q) => q.workstreamType))];
  const outstanding = questions.filter((q) => q.status === 'OPEN').length;

  return (
    <section>
      <div className="page-header"><h2>Diligence questions</h2></div>
      <p className="hint">
        {outstanding === 0
          ? 'Every question has been answered or ruled out.'
          : `${outstanding} of ${questions.length} still unanswered.`} An answer records what it rests
        on, and is accepted by someone other than the person who wrote it.
      </p>

      {areas.map((area) => (
        <div key={area}>
          <h3 className="eyebrow">{humanize(area)}</h3>
          <table className="data-table">
            <thead><tr><th>Question</th><th>Status</th><th>Answered by</th><th></th></tr></thead>
            <tbody>
              {questions.filter((q) => q.workstreamType === area).map((q) => (
                <tr key={q.id}>
                  <td>
                    {q.question}
                    {q.answer && <div className="hint">{q.answer}</div>}
                    {q.notApplicableReason && <div className="hint">Not applicable: {q.notApplicableReason}</div>}
                    {openId === q.id && (
                      <AnswerForm question={q} facts={facts} evidence={evidence}
                        onAnswer={async (...args) => { await onAnswer(...args); setOpenId(null); }} />
                    )}
                  </td>
                  <td>
                    <span className={STATUS_BADGE[q.status]}>{humanize(q.status)}</span>
                    {q.severity === 'BLOCKING' && q.status === 'OPEN' && <div className="hint">Blocking</div>}
                  </td>
                  <td>{q.answeredBy ? q.answeredBy.fullName : '—'}</td>
                  <td>
                    <button className="secondary" onClick={() => setOpenId(openId === q.id ? null : q.id)}>
                      {openId === q.id ? 'Cancel' : (q.status === 'OPEN' ? 'Answer' : 'Revise')}
                    </button>
                    {q.status === 'ANSWERED' && q.answeredBy?.id !== currentUserId && (
                      <button onClick={() => onAccept(q.id)}>Accept</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}
    </section>
  );
}
