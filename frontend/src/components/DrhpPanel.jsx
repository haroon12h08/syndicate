import { useState } from 'react';
import { Link } from 'react-router-dom';
import { humanize } from '../constants';

function disclosureBadge(status) {
  if (status === 'READY') return 'badge badge-complete';
  if (status === 'STALE_REQUIRING_REVIEW') return 'badge badge-failed';
  return 'badge badge-pending';
}

const EMPTY_FORM = { sectionCode: '', title: '', bodyTemplate: '', orderIndex: 0, status: 'DRAFT' };

export default function DrhpPanel({
  disclosures, document: drhpDocument, compileResult, compiling,
  onCompile, onCreateDisclosure, onUpdateDisclosure,
}) {
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState(null);
  const [editForm, setEditForm] = useState(EMPTY_FORM);
  const [tracedFact, setTracedFact] = useState(null);

  const sections = compileResult?.preview || drhpDocument?.sections || [];
  const findings = compileResult?.findings || [];

  function startEdit(disclosure) {
    setEditingId(disclosure.id);
    setEditForm({
      sectionCode: disclosure.sectionCode,
      title: disclosure.title,
      bodyTemplate: disclosure.bodyTemplate,
      orderIndex: disclosure.orderIndex,
      status: disclosure.status,
    });
  }

  return (
    <section className="drhp-panel">
      <div className="page-header">
        <h2>DRHP</h2>
        <div className="inline-form">
          <button onClick={() => setShowForm((s) => !s)} className="secondary">
            {showForm ? 'Cancel' : 'New disclosure'}
          </button>
          <button onClick={onCompile} disabled={compiling}>
            {compiling ? 'Compiling...' : 'Compile DRHP'}
          </button>
        </div>
      </div>

      {drhpDocument && (
        <div className={`drhp-status ${drhpDocument.status === 'COMPILED' ? 'ok' : 'invalid'}`}>
          <strong>v{drhpDocument.version} — {humanize(drhpDocument.status)}</strong>
          <div className="hint">
            {drhpDocument.status === 'COMPILED'
              ? drhpDocument.lintSummary
              : drhpDocument.invalidatedReason}
            {drhpDocument.compiledAt && <> · compiled {new Date(drhpDocument.compiledAt).toLocaleString()}</>}
          </div>
        </div>
      )}

      {compileResult && !compileResult.compiled && (
        <div className="lint-report">
          <strong>Compilation refused — {findings.length} blocking issue(s)</strong>
          <ul>
            {findings.map((f, i) => (
              <li key={i}>
                <code>{f.code}</code>
                {f.sectionCode && <> · {f.sectionCode}</>} — {f.message}
              </li>
            ))}
          </ul>
        </div>
      )}

      {compileResult?.compiled && (
        <div className="lint-report clean"><strong>Compiled cleanly — every printed value traces to a verified fact.</strong></div>
      )}

      {showForm && (
        <form
          className="card-form"
          onSubmit={(e) => {
            e.preventDefault();
            onCreateDisclosure(form).then(() => { setForm(EMPTY_FORM); setShowForm(false); });
          }}
        >
          <label>
            Section code
            <input value={form.sectionCode} onChange={(e) => setForm((f) => ({ ...f, sectionCode: e.target.value }))} required />
          </label>
          <label>
            Title
            <input value={form.title} onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))} required />
          </label>
          <label>
            Body template
            <textarea
              rows={4}
              value={form.bodyTemplate}
              onChange={(e) => setForm((f) => ({ ...f, bodyTemplate: e.target.value }))}
              placeholder="Use {{fact:Label}} to cite a verified fact."
              required
            />
            <span className="hint">{'Cite facts with {{fact:Exact Fact Label}} — only human-verified facts resolve.'}</span>
          </label>
          <button type="submit">Create disclosure</button>
        </form>
      )}

      <h3 className="eyebrow">Disclosures</h3>
      <table className="data-table">
        <thead>
          <tr><th>Section</th><th>Title</th><th>Status</th><th>Sources</th><th></th></tr>
        </thead>
        <tbody>
          {disclosures.map((d) => (
            <tr key={d.id}>
              <td><code>{d.sectionCode}</code></td>
              <td>
                {d.title}
                {d.staleReason && <div className="hint">{d.staleReason}</div>}
              </td>
              <td><span className={disclosureBadge(d.status)}>{humanize(d.status)}</span></td>
              <td>{d.sourceFactIds.length}</td>
              <td><button className="secondary" onClick={() => startEdit(d)}>Edit</button></td>
            </tr>
          ))}
          {disclosures.length === 0 && (
            <tr><td colSpan={5} className="hint">No disclosures yet.</td></tr>
          )}
        </tbody>
      </table>

      {editingId && (
        <div className="drawer-backdrop" onClick={() => setEditingId(null)}>
          <aside className="drawer" onClick={(e) => e.stopPropagation()}>
            <div className="page-header">
              <h3>Edit disclosure</h3>
              <button className="secondary" onClick={() => setEditingId(null)}>Close</button>
            </div>
            <label>
              Title
              <input value={editForm.title} onChange={(e) => setEditForm((f) => ({ ...f, title: e.target.value }))} />
            </label>
            <label>
              Body template
              <textarea rows={6} value={editForm.bodyTemplate}
                onChange={(e) => setEditForm((f) => ({ ...f, bodyTemplate: e.target.value }))} />
            </label>
            <label>
              Status
              <select value={editForm.status} onChange={(e) => setEditForm((f) => ({ ...f, status: e.target.value }))}>
                <option value="DRAFT">Draft</option>
                <option value="READY">Ready</option>
              </select>
              <span className="hint">Marking a stale section Ready records that a human re-read it.</span>
            </label>
            <button
              onClick={() => onUpdateDisclosure(editingId, editForm).then(() => setEditingId(null))}
            >
              Save disclosure
            </button>
          </aside>
        </div>
      )}

      {sections.length > 0 && (
        <>
          <h3 className="eyebrow">
            {compileResult && !compileResult.compiled ? 'Preview (not compiled)' : 'Compiled document'}
          </h3>
          <article className="drhp-document">
            {sections.map((section) => (
              <div key={section.sectionCode} className="drhp-section">
                <h4>{section.title}</h4>
                <p>
                  {section.segments.map((seg, i) => {
                    if (seg.type === 'TEXT') return <span key={i}>{seg.text}</span>;
                    if (seg.type === 'FACT') {
                      return (
                        <button
                          key={i}
                          type="button"
                          className="fact-chip"
                          title={`${seg.label} — click to trace to evidence`}
                          onClick={() => setTracedFact(seg)}
                        >
                          {seg.value}{seg.unit ? ` ${seg.unit}` : ''}
                        </button>
                      );
                    }
                    return <span key={i} className="unresolved-chip">[{seg.label} — unresolved]</span>;
                  })}
                </p>
              </div>
            ))}
          </article>
        </>
      )}

      {tracedFact && (
        <div className="drawer-backdrop" onClick={() => setTracedFact(null)}>
          <aside className="drawer" onClick={(e) => e.stopPropagation()}>
            <div className="page-header">
              <h3>Evidence trail</h3>
              <button className="secondary" onClick={() => setTracedFact(null)}>Close</button>
            </div>
            <div className="detail-grid">
              <div><strong>Fact</strong><span>{tracedFact.label}</span></div>
              <div><strong>Printed value</strong><span>{tracedFact.value} {tracedFact.unit}</span></div>
              <div><strong>Fact id</strong><span><code>{tracedFact.factId}</code></span></div>
              <div><strong>Linked evidence</strong><span>{tracedFact.evidenceIds.length} document(s)</span></div>
            </div>
            <p className="hint">
              This value was printed from a human-verified fact. Open the fact to see its full version
              history and the page regions its evidence was anchored to.
            </p>
            <Link className="drawer-cta" to={`/facts/${tracedFact.factId}/trace`}>
              Open evidence trace
            </Link>
          </aside>
        </div>
      )}
    </section>
  );
}
