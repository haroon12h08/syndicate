import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as factsApi from '../api/facts';
import * as evidenceApi from '../api/evidence';
import { humanize } from '../constants';

export default function FactTracePage() {
  const { id } = useParams();
  const [trace, setTrace] = useState(null);
  const [imageUrl, setImageUrl] = useState(null);
  const [imageError, setImageError] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    factsApi.getFactTrace(id).then(setTrace).catch((err) => setError(err.message));
  }, [id]);

  useEffect(() => {
    const origin = trace?.origin;
    if (!origin) return undefined;
    let objectUrl = null;
    let cancelled = false;
    evidenceApi.pageImageUrl(origin.evidenceId, origin.pageNumber)
      .then((url) => {
        if (cancelled) {
          window.URL.revokeObjectURL(url);
          return;
        }
        objectUrl = url;
        setImageUrl(url);
      })
      .catch((err) => setImageError(err.message));
    return () => {
      cancelled = true;
      if (objectUrl) window.URL.revokeObjectURL(objectUrl);
    };
  }, [trace]);

  if (error) return <div className="page"><div className="error-banner">{error}</div></div>;
  if (!trace) return <div className="page">Loading...</div>;

  const { fact, evidence, origin } = trace;
  const box = origin && {
    left: `${(origin.bboxX / origin.pageImageWidth) * 100}%`,
    top: `${(origin.bboxY / origin.pageImageHeight) * 100}%`,
    width: `${(origin.bboxWidth / origin.pageImageWidth) * 100}%`,
    height: `${(origin.bboxHeight / origin.pageImageHeight) * 100}%`,
  };

  return (
    <div className="page">
      <h1>{fact.label}</h1>
      <p className="hint">Evidence trail for a value printed in the DRHP.</p>

      <div className="detail-grid">
        <div><strong>Value</strong><span>{fact.value} {fact.unit}</span></div>
        <div><strong>Status</strong><span>{humanize(fact.status)}</span></div>
        <div><strong>Version</strong><span>v{fact.version}</span></div>
        <div>
          <strong>Verified by</strong>
          <span>{fact.verifiedBy ? fact.verifiedBy.fullName : 'Not verified'}</span>
        </div>
        <div>
          <strong>Business validity</strong>
          <span>
            {fact.validFrom ? new Date(fact.validFrom).toLocaleDateString() : '—'} →{' '}
            {fact.validTo ? new Date(fact.validTo).toLocaleDateString() : 'current'}
          </span>
        </div>
        <div>
          <strong>Workstream</strong>
          <span><Link to={`/workstreams/${fact.workstreamId}`}>Open workstream</Link></span>
        </div>
      </div>

      <h2>Source document</h2>
      {!origin && (
        <p className="hint">
          This fact was entered directly rather than extracted from a document, so it has no page anchor.
        </p>
      )}

      {origin && (
        <>
          <div className="detail-grid">
            <div><strong>File</strong><span>{origin.evidenceFileName}</span></div>
            <div><strong>Page</strong><span>{origin.pageNumber}</span></div>
            <div><strong>Extraction</strong><span>{humanize(origin.source)}</span></div>
            <div>
              <strong>Accepted by</strong>
              <span>
                {origin.reviewedByName}
                {origin.reviewedAt && ` · ${new Date(origin.reviewedAt).toLocaleString()}`}
              </span>
            </div>
          </div>

          {imageError && <div className="hint">Could not load source page: {imageError}</div>}
          {imageUrl && (
            <div className="candidate-fact-image-frame">
              <img src={imageUrl} alt={`Page ${origin.pageNumber} of ${origin.evidenceFileName}`} />
              <div className="candidate-fact-bbox" style={box} />
            </div>
          )}
        </>
      )}

      <h2>Linked evidence</h2>
      <ul className="entity-list">
        {evidence.map((e) => (
          <li key={e.id}>{e.fileName} <span className="hint">{humanize(e.documentType || '')}</span></li>
        ))}
        {evidence.length === 0 && <li className="hint">No evidence linked to this fact.</li>}
      </ul>
    </div>
  );
}
