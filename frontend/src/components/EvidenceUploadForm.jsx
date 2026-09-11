import { useState } from 'react';
import { EVIDENCE_DOCUMENT_TYPES, humanize } from '../constants';

export default function EvidenceUploadForm({ onUpload, onCancel }) {
  const [file, setFile] = useState(null);
  const [documentType, setDocumentType] = useState(EVIDENCE_DOCUMENT_TYPES[0]);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!file) return;
    setSubmitting(true);
    try {
      await onUpload(file, documentType);
      setFile(null);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="card-form" onSubmit={handleSubmit}>
      <label>
        File
        <input type="file" onChange={(e) => setFile(e.target.files[0] || null)} required />
      </label>
      <label>
        Document type
        <select value={documentType} onChange={(e) => setDocumentType(e.target.value)}>
          {EVIDENCE_DOCUMENT_TYPES.map((t) => (
            <option key={t} value={t}>{humanize(t)}</option>
          ))}
        </select>
      </label>
      <div className="form-actions">
        <button type="submit" disabled={submitting || !file}>{submitting ? 'Uploading...' : 'Upload'}</button>
        {onCancel && <button type="button" className="secondary" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  );
}
