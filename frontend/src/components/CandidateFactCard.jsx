import { useEffect, useState } from 'react';
import * as evidenceApi from '../api/evidence';

export default function CandidateFactCard({ candidate, onAccept, onReject }) {
  const [imageUrl, setImageUrl] = useState(null);
  const [imageError, setImageError] = useState(null);
  const [label, setLabel] = useState(candidate.label);
  const [value, setValue] = useState(candidate.value);
  const [period, setPeriod] = useState(candidate.period || '');
  const [reviewNote, setReviewNote] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let objectUrl = null;
    let cancelled = false;
    evidenceApi.pageImageUrl(candidate.evidenceId, candidate.pageNumber)
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
  }, [candidate.evidenceId, candidate.pageNumber]);

  const leftPct = (candidate.bboxX / candidate.pageImageWidth) * 100;
  const topPct = (candidate.bboxY / candidate.pageImageHeight) * 100;
  const widthPct = (candidate.bboxWidth / candidate.pageImageWidth) * 100;
  const heightPct = (candidate.bboxHeight / candidate.pageImageHeight) * 100;

  async function handleAccept() {
    setSubmitting(true);
    try {
      await onAccept(candidate.id, { label, value, period: period || null, reviewNote: reviewNote || null });
    } finally {
      setSubmitting(false);
    }
  }

  async function handleReject() {
    setSubmitting(true);
    try {
      await onReject(candidate.id, { reviewNote: reviewNote || null });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="candidate-fact-card">
      <div className="candidate-fact-image-wrap">
        {imageError && <div className="hint">Could not load source page: {imageError}</div>}
        {imageUrl && (
          <div className="candidate-fact-image-frame">
            <img src={imageUrl} alt={`Page ${candidate.pageNumber} of evidence`} />
            <div
              className="candidate-fact-bbox"
              style={{ left: `${leftPct}%`, top: `${topPct}%`, width: `${widthPct}%`, height: `${heightPct}%` }}
            />
          </div>
        )}
      </div>
      <div className="candidate-fact-fields">
        <span className="hint">
          Page {candidate.pageNumber} &middot; {candidate.source === 'OCR' ? 'OCR' : 'Text layer'}
        </span>
        <label>
          Label
          <input value={label} onChange={(e) => setLabel(e.target.value)} />
        </label>
        <label>
          Value
          <input value={value} onChange={(e) => setValue(e.target.value)} />
        </label>
        <label>
          Period
          <input value={period} onChange={(e) => setPeriod(e.target.value)} />
        </label>
        <label>
          Review note
          <input value={reviewNote} onChange={(e) => setReviewNote(e.target.value)} placeholder="optional" />
        </label>
        <div className="form-actions">
          <button disabled={submitting} onClick={handleAccept}>{submitting ? 'Saving...' : 'Accept'}</button>
          <button disabled={submitting} className="secondary" onClick={handleReject}>Reject</button>
        </div>
      </div>
    </div>
  );
}
