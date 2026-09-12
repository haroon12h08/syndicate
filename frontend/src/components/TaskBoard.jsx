import { useState } from 'react';
import { Link } from 'react-router-dom';
import { humanize } from '../constants';

const COLUMNS = [
  { key: 'TODO', label: 'To do' },
  { key: 'IN_PROGRESS', label: 'In progress' },
  { key: 'IN_REVIEW', label: 'In review' },
  { key: 'RESOLVED', label: 'Resolved' },
];

function severityClass(severity) {
  if (severity === 'BLOCKING' || severity === 'CRITICAL') return 'badge badge-failed';
  if (severity === 'HIGH') return 'badge badge-pending';
  return 'badge badge-not_applicable';
}

export default function TaskBoard({ tasks, members, onMove, onAssign, onResolutionNote, onInviteAdvisor }) {
  const [openTask, setOpenTask] = useState(null);
  const [note, setNote] = useState('');
  const [dragTaskId, setDragTaskId] = useState(null);

  const escalated = tasks.filter((t) => t.status === 'UNASSIGNED_ESCALATED');
  const selected = openTask ? tasks.find((t) => t.id === openTask) : null;

  function openDrawer(task) {
    setOpenTask(task.id);
    setNote(task.resolutionNote || '');
  }

  return (
    <section className="task-board-section">
      <div className="page-header"><h2>Task board</h2></div>

      {escalated.map((task) => (
        <div key={task.id} className="escalation-banner">
          <div>
            <strong>Unassigned task:</strong> {humanize(task.requiredRole || 'Advisor')} needed for{' '}
            {humanize(task.workstreamType || 'this transaction')}.
            <div className="hint">{task.title}</div>
          </div>
          <button onClick={onInviteAdvisor}>Invite Advisor Now</button>
        </div>
      ))}

      <div className="kanban">
        {COLUMNS.map((column) => {
          const columnTasks = tasks.filter((t) =>
            column.key === 'TODO'
              ? t.status === 'TODO' || t.status === 'UNASSIGNED_ESCALATED'
              : t.status === column.key
          );
          return (
            <div
              key={column.key}
              className="kanban-column"
              onDragOver={(e) => e.preventDefault()}
              onDrop={() => {
                if (dragTaskId) onMove(dragTaskId, column.key);
                setDragTaskId(null);
              }}
            >
              <div className="kanban-column-head">
                {column.label} <span className="hint">{columnTasks.length}</span>
              </div>
              {columnTasks.map((task) => (
                <article
                  key={task.id}
                  className={`kanban-card${task.status === 'UNASSIGNED_ESCALATED' ? ' escalated' : ''}`}
                  draggable
                  onDragStart={() => setDragTaskId(task.id)}
                  onClick={() => openDrawer(task)}
                >
                  <div className="kanban-card-title">{task.title}</div>
                  <div className="kanban-card-meta">
                    <span className={severityClass(task.severity)}>{humanize(task.severity)}</span>
                    {task.workstreamType && <span className="hint">{humanize(task.workstreamType)}</span>}
                  </div>
                  <div className="hint">
                    {task.assignedUser
                      ? task.assignedUser.fullName
                      : `Unassigned — needs ${humanize(task.requiredRole || 'advisor')}`}
                  </div>
                  <select
                    className="kanban-card-move"
                    value={task.status === 'UNASSIGNED_ESCALATED' ? 'TODO' : task.status}
                    onClick={(e) => e.stopPropagation()}
                    onChange={(e) => onMove(task.id, e.target.value)}
                  >
                    {COLUMNS.map((c) => (
                      <option key={c.key} value={c.key}>{c.label}</option>
                    ))}
                  </select>
                </article>
              ))}
              {columnTasks.length === 0 && <p className="hint kanban-empty">Nothing here.</p>}
            </div>
          );
        })}
      </div>

      {selected && (
        <div className="drawer-backdrop" onClick={() => setOpenTask(null)}>
          <aside className="drawer" onClick={(e) => e.stopPropagation()}>
            <div className="page-header">
              <h3>{selected.title}</h3>
              <button className="secondary" onClick={() => setOpenTask(null)}>Close</button>
            </div>

            <span className={severityClass(selected.severity)}>{humanize(selected.severity)}</span>
            <p>{selected.description}</p>

            <div className="detail-grid">
              <div><strong>Status</strong><span>{humanize(selected.status)}</span></div>
              <div>
                <strong>Workstream</strong>
                <span>
                  {selected.workstreamId
                    ? <Link to={`/workstreams/${selected.workstreamId}`}>{humanize(selected.workstreamType)}</Link>
                    : 'Not workstream-specific'}
                </span>
              </div>
              <div><strong>Required role</strong><span>{humanize(selected.requiredRole || '—')}</span></div>
              <div>
                <strong>Origin</strong>
                <span>{selected.sourceRuleId ? 'Regulatory rule evaluation' : 'Created manually'}</span>
              </div>
            </div>

            <label>
              Assigned to
              <select
                value={selected.assignedUser?.id || ''}
                onChange={(e) => onAssign(selected.id, e.target.value)}
              >
                <option value="">Unassigned</option>
                {members.map((m) => (
                  <option key={m.user.id} value={m.user.id}>
                    {m.user.fullName} — {humanize(m.role)}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Resolution notes
              <textarea rows={4} value={note} onChange={(e) => setNote(e.target.value)} />
            </label>
            <button onClick={() => onResolutionNote(selected.id, note)}>Save resolution</button>
          </aside>
        </div>
      )}
    </section>
  );
}
