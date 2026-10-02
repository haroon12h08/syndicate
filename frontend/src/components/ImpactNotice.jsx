import { useEffect, useState } from 'react';
import * as factsApi from '../api/facts';

const WORDING = {
  DISCLOSURE: 'Disclosure',
  DOCUMENT: 'Document',
  REVIEW: 'Review',
  APPROVAL: 'Approval',
};

/**
 * What this change will reach, shown while the change is still being written. The consequences of
 * correcting a figure are the part people cannot hold in their heads; the system can.
 */
export default function ImpactNotice({ factId }) {
  const [impact, setImpact] = useState(null);

  useEffect(() => {
    let cancelled = false;
    factsApi.getFactImpact(factId)
      .then((result) => { if (!cancelled) setImpact(result); })
      .catch(() => setImpact(null));
    return () => { cancelled = true; };
  }, [factId]);

  if (!impact || impact.affected.length === 0) return null;

  return (
    <div className="impact-notice">
      <strong>Correcting this reaches {impact.affected.length} other thing{impact.affected.length > 1 ? 's' : ''}:</strong>
      <ul>
        {impact.affected.map((a) => (
          <li key={`${a.type}-${a.id}`}>
            <span className="badge badge-pending">{WORDING[a.type] || a.type}</span> {a.title} — {a.consequence}
          </li>
        ))}
      </ul>
      <span className="hint">Nothing is lost: the old value stays on record, and everything above is flagged for a person to revisit.</span>
    </div>
  );
}
