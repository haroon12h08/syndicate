import { humanize } from '../constants';

export default function AuditTimeline({ events }) {
  return (
    <section>
      <div className="page-header"><h2>Audit trail</h2></div>
      <p className="hint">
        Every material change, append-only. Nothing here can be edited or removed.
      </p>

      <ol className="timeline">
        {events.map((e) => (
          <li key={e.id}>
            <div className="timeline-time">{new Date(e.occurredAt).toLocaleString()}</div>
            <div className="timeline-body">
              <div className="timeline-head">
                <span className="badge badge-not_applicable">{humanize(e.action)}</span>
                <strong>{e.actorName}</strong>
              </div>
              <div>{e.summary}</div>
              {(e.previousValue || e.newValue) && (
                <div className="hint">
                  {e.previousValue ? `from ${e.previousValue} ` : ''}
                  {e.newValue ? `to ${e.newValue}` : ''}
                </div>
              )}
              {e.reason && <div className="hint">Reason: {e.reason}</div>}
            </div>
          </li>
        ))}
        {events.length === 0 && (
          <li className="empty-state"><span>No recorded activity yet.</span></li>
        )}
      </ol>
    </section>
  );
}
