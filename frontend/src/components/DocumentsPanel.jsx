import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { EVIDENCE_DOCUMENT_TYPES, humanize } from '../constants';

function statusBadge(status) {
  if (status === 'COMPLETED') return 'badge badge-complete';
  if (status === 'FAILED') return 'badge badge-failed';
  if (status === 'PROCESSING' || status === 'PENDING') return 'badge badge-pending';
  return 'badge badge-not_applicable';
}

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function DocumentsPanel({
  evidence, workstreams, onUpload, onDelete, onReprocess, onDownload,
}) {
  const [workstreamId, setWorkstreamId] = useState('');
  const [documentType, setDocumentType] = useState(EVIDENCE_DOCUMENT_TYPES[0]);
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [dragging, setDragging] = useState(false);
  const fileInput = useRef(null);

  const targetWorkstream = workstreamId || workstreams[0]?.id || '';

  async function submitUpload(e) {
    e?.preventDefault();
    if (!file || !targetWorkstream) return;
    setUploading(true);
    try {
      await onUpload(targetWorkstream, file, documentType);
      setFile(null);
      if (fileInput.current) fileInput.current.value = '';
    } finally {
      setUploading(false);
    }
  }

  const workstreamName = (id) => {
    const ws = workstreams.find((w) => w.id === id);
    return ws ? humanize(ws.type) : '—';
  };

  return (
    <section>
      <div className="page-header"><h2>Documents</h2></div>

      {workstreams.length === 0 ? (
        <p className="hint">
          Create a workstream first — documents are filed against the workstream they belong to.
        </p>
      ) : (
        <form
          className={`upload-dropzone${dragging ? ' dragging' : ''}`}
          onSubmit={submitUpload}
          onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
          onDragLeave={() => setDragging(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragging(false);
            if (e.dataTransfer.files?.[0]) setFile(e.dataTransfer.files[0]);
          }}
        >
          <div className="upload-dropzone-main">
            <strong>{file ? file.name : 'Drop a PDF here, or choose a file'}</strong>
            <span className="hint">
              Uploaded documents are checksummed, parsed for a text layer, OCR'd when there isn't
              one, and turned into candidate facts for human review.
            </span>
            <input
              ref={fileInput}
              type="file"
              accept="application/pdf,image/*"
              onChange={(e) => setFile(e.target.files?.[0] || null)}
            />
          </div>
          <div className="upload-dropzone-controls">
            <label>
              Workstream
              <select value={targetWorkstream} onChange={(e) => setWorkstreamId(e.target.value)}>
                {workstreams.map((w) => (
                  <option key={w.id} value={w.id}>{humanize(w.type)}</option>
                ))}
              </select>
            </label>
            <label>
              Document type
              <select value={documentType} onChange={(e) => setDocumentType(e.target.value)}>
                {EVIDENCE_DOCUMENT_TYPES.map((t) => (
                  <option key={t} value={t}>{humanize(t)}</option>
                ))}
              </select>
            </label>
            <button type="submit" disabled={!file || uploading}>
              {uploading ? 'Uploading...' : 'Upload document'}
            </button>
          </div>
        </form>
      )}

      <table className="data-table">
        <thead>
          <tr>
            <th>File</th><th>Workstream</th><th>Type</th><th>Size</th>
            <th>Processing</th><th>Checksum</th><th></th>
          </tr>
        </thead>
        <tbody>
          {evidence.map((e) => (
            <tr key={e.id}>
              <td>
                {e.fileName}
                <div className="hint">
                  {e.uploadedBy?.fullName} · {new Date(e.uploadedAt).toLocaleString()}
                </div>
              </td>
              <td>
                <Link to={`/workstreams/${e.workstreamId}`}>{workstreamName(e.workstreamId)}</Link>
              </td>
              <td>{humanize(e.documentType)}</td>
              <td>{formatSize(e.fileSizeBytes)}</td>
              <td>
                <span className={statusBadge(e.processingStatus)}>{humanize(e.processingStatus)}</span>
                {e.processingError && <div className="hint">{e.processingError}</div>}
              </td>
              <td><code className="hint">{e.fileSha256?.slice(0, 10)}</code></td>
              <td className="row-actions">
                <button className="secondary" onClick={() => onDownload(e.id)}>Download</button>
                <button className="secondary" onClick={() => onReprocess(e.id)}>Reprocess</button>
                <button className="danger" onClick={() => onDelete(e.id, e.fileName)}>Delete</button>
              </td>
            </tr>
          ))}
          {evidence.length === 0 && (
            <tr><td colSpan={7} className="hint">No documents uploaded yet.</td></tr>
          )}
        </tbody>
      </table>
    </section>
  );
}
