import { useEffect, useRef, useState, useCallback } from 'react';
import * as evidenceApi from '../api/evidence';
import * as candidateFactsApi from '../api/candidateFacts';
import { humanize } from '../constants';

export default function SpatialEvidenceViewer({
  evidenceId,
  targetFactId = null,
  targetCandidateFactId = null,
  onClose,
  onFactUpdated,
}) {
  const [manifest, setManifest] = useState(null);
  const [loadingManifest, setLoadingManifest] = useState(true);
  const [manifestError, setManifestError] = useState(null);

  const [currentPage, setCurrentPage] = useState(1);
  const [zoom, setZoom] = useState(100); // 50 to 200 percent
  const [showBoxes, setShowBoxes] = useState(true);

  const [imageUrl, setImageUrl] = useState(null);
  const [loadingImage, setLoadingImage] = useState(false);
  const [imageError, setImageError] = useState(null);

  const [selectedAnchor, setSelectedAnchor] = useState(null);
  const [hoveredAnchor, setHoveredAnchor] = useState(null);
  const [sidebarTab, setSidebarTab] = useState('page'); // 'page' | 'all' | 'facts'
  const [sidebarOpen, setSidebarOpen] = useState(true);

  // Review action fields
  const [reviewLabel, setReviewLabel] = useState('');
  const [reviewValue, setReviewValue] = useState('');
  const [reviewPeriod, setReviewPeriod] = useState('');
  const [reviewNote, setReviewNote] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState(null);

  const pageContainerRef = useRef(null);

  // Load manifest
  const loadManifest = useCallback(async () => {
    if (!evidenceId) return;
    setLoadingManifest(true);
    setManifestError(null);
    try {
      const data = await evidenceApi.getEvidenceManifest(evidenceId);
      setManifest(data);

      // Auto-navigate to targeted fact/candidate if provided
      if (targetFactId) {
        const matchingAnchor = data.anchors?.find((a) => a.resultingFactId === targetFactId);
        if (matchingAnchor) {
          setCurrentPage(matchingAnchor.pageNumber);
          setSelectedAnchor(matchingAnchor);
        } else {
          const matchingFact = data.linkedFacts?.find((f) => f.id === targetFactId);
          if (matchingFact?.pageNumber) {
            setCurrentPage(matchingFact.pageNumber);
          }
        }
      } else if (targetCandidateFactId) {
        const matchingAnchor = data.anchors?.find((a) => a.candidateFactId === targetCandidateFactId);
        if (matchingAnchor) {
          setCurrentPage(matchingAnchor.pageNumber);
          setSelectedAnchor(matchingAnchor);
        }
      }
    } catch (err) {
      setManifestError(err.message || 'Failed to load document manifest');
    } finally {
      setLoadingManifest(false);
    }
  }, [evidenceId, targetFactId, targetCandidateFactId]);

  useEffect(() => {
    loadManifest();
  }, [loadManifest]);

  // Load page image when currentPage changes
  useEffect(() => {
    if (!evidenceId) return undefined;
    let objectUrl = null;
    let cancelled = false;

    setLoadingImage(true);
    setImageError(null);

    evidenceApi.pageImageUrl(evidenceId, currentPage)
      .then((url) => {
        if (cancelled) {
          window.URL.revokeObjectURL(url);
          return;
        }
        objectUrl = url;
        setImageUrl(url);
        setLoadingImage(false);
      })
      .catch((err) => {
        if (!cancelled) {
          setImageError(err.message || 'Failed to load page image');
          setLoadingImage(false);
        }
      });

    return () => {
      cancelled = true;
      if (objectUrl) window.URL.revokeObjectURL(objectUrl);
    };
  }, [evidenceId, currentPage]);

  // When selected anchor changes, update review fields
  useEffect(() => {
    if (selectedAnchor) {
      setReviewLabel(selectedAnchor.label || '');
      setReviewValue(selectedAnchor.value || '');
      setReviewPeriod(selectedAnchor.period || '');
      setReviewNote('');
      setActionError(null);
    }
  }, [selectedAnchor]);

  // Keyboard navigation
  useEffect(() => {
    function handleKeyDown(e) {
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.tagName === 'SELECT') {
        return;
      }
      if (e.key === 'Escape') {
        onClose();
      } else if ((e.key === 'ArrowRight' || e.key === 'PageDown') && manifest) {
        if (currentPage < manifest.pageCount) {
          setCurrentPage((p) => p + 1);
        }
      } else if ((e.key === 'ArrowLeft' || e.key === 'PageUp') && manifest) {
        if (currentPage > 1) {
          setCurrentPage((p) => p - 1);
        }
      } else if (e.key === '+' || e.key === '=') {
        setZoom((z) => Math.min(z + 20, 200));
      } else if (e.key === '-' || e.key === '_') {
        setZoom((z) => Math.max(z - 20, 50));
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [manifest, currentPage, onClose]);

  // Direct Candidate Fact Accept
  async function handleAccept() {
    if (!selectedAnchor) return;
    setActionLoading(true);
    setActionError(null);
    try {
      await candidateFactsApi.acceptCandidateFact(selectedAnchor.candidateFactId, {
        label: reviewLabel,
        value: reviewValue,
        period: reviewPeriod || null,
        reviewNote: reviewNote || null,
      });
      await loadManifest();
      if (onFactUpdated) onFactUpdated();
    } catch (err) {
      setActionError(err.message || 'Failed to accept candidate fact');
    } finally {
      setActionLoading(false);
    }
  }

  // Direct Candidate Fact Reject
  async function handleReject() {
    if (!selectedAnchor) return;
    setActionLoading(true);
    setActionError(null);
    try {
      await candidateFactsApi.rejectCandidateFact(selectedAnchor.candidateFactId, {
        reviewNote: reviewNote || null,
      });
      await loadManifest();
      if (onFactUpdated) onFactUpdated();
    } catch (err) {
      setActionError(err.message || 'Failed to reject candidate fact');
    } finally {
      setActionLoading(false);
    }
  }

  const pageCount = manifest?.pageCount || 1;
  const pageAnchors = (manifest?.anchors || []).filter((a) => a.pageNumber === currentPage);
  const allAnchors = manifest?.anchors || [];
  const linkedFacts = manifest?.linkedFacts || [];

  const isAnchorTargeted = (anchor) => {
    if (selectedAnchor && selectedAnchor.candidateFactId === anchor.candidateFactId) return true;
    if (targetFactId && anchor.resultingFactId === targetFactId) return true;
    if (targetCandidateFactId && anchor.candidateFactId === targetCandidateFactId) return true;
    return false;
  };

  const getAnchorClassName = (anchor) => {
    const classes = ['spatial-bbox'];
    if (anchor.isVerified) {
      classes.push('verified');
    } else if (anchor.status === 'PENDING') {
      classes.push('pending');
    } else if (anchor.status === 'REJECTED') {
      classes.push('rejected');
    } else {
      classes.push('accepted');
    }
    if (isAnchorTargeted(anchor)) {
      classes.push('targeted');
    }
    return classes.join(' ');
  };

  return (
    <div className="spatial-viewer-backdrop" onClick={onClose}>
      <aside className="spatial-viewer-modal" onClick={(e) => e.stopPropagation()}>
        {/* Viewer Top Header */}
        <header className="spatial-viewer-header">
          <div className="spatial-viewer-title-group">
            <span className="spatial-viewer-badge">SPATIAL EVIDENCE GROUNDING</span>
            <h3 className="spatial-viewer-title">{manifest?.fileName || 'Document Viewer'}</h3>
            {manifest?.fileSha256 && (
              <code className="spatial-viewer-hash" title={`Full SHA-256: ${manifest.fileSha256}`}>
                SHA: {manifest.fileSha256.slice(0, 8)}…
              </code>
            )}
            {manifest?.documentType && (
              <span className="badge badge-complete">{humanize(manifest.documentType)}</span>
            )}
          </div>

          <div className="spatial-viewer-actions">
            <button
              type="button"
              className="secondary"
              onClick={() => setSidebarOpen((s) => !s)}
              title="Toggle Grounding Sidebar"
            >
              {sidebarOpen ? 'Hide Inspector' : 'Show Inspector'}
            </button>
            <button
              type="button"
              className="spatial-viewer-close"
              onClick={onClose}
              title="Close Viewer (Esc)"
            >
              ✕
            </button>
          </div>
        </header>

        {/* Viewer Toolbar: Page nav, Zoom, Highlights toggle */}
        <div className="spatial-viewer-toolbar">
          <div className="spatial-toolbar-pages">
            <button
              type="button"
              className="secondary page-btn"
              disabled={currentPage <= 1}
              onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
              title="Previous Page (Left Arrow)"
            >
              ◀ Prev
            </button>
            <span className="page-counter">
              Page
              <select
                value={currentPage}
                onChange={(e) => setCurrentPage(Number(e.target.value))}
                className="page-select"
              >
                {Array.from({ length: pageCount }, (_, i) => i + 1).map((p) => (
                  <option key={p} value={p}>{p}</option>
                ))}
              </select>
              of {pageCount}
            </span>
            <button
              type="button"
              className="secondary page-btn"
              disabled={currentPage >= pageCount}
              onClick={() => setCurrentPage((p) => Math.min(pageCount, p + 1))}
              title="Next Page (Right Arrow)"
            >
              Next ▶
            </button>
          </div>

          <div className="spatial-toolbar-zoom">
            <button
              type="button"
              className="secondary zoom-btn"
              disabled={zoom <= 50}
              onClick={() => setZoom((z) => Math.max(z - 20, 50))}
              title="Zoom Out (-)"
            >
              -
            </button>
            <span className="zoom-label">{zoom}%</span>
            <button
              type="button"
              className="secondary zoom-btn"
              disabled={zoom >= 200}
              onClick={() => setZoom((z) => Math.min(z + 20, 200))}
              title="Zoom In (+)"
            >
              +
            </button>
            <button
              type="button"
              className="secondary"
              onClick={() => setZoom(100)}
            >
              Fit Width
            </button>
          </div>

          <div className="spatial-toolbar-toggles">
            <label className="toggle-label">
              <input
                type="checkbox"
                checked={showBoxes}
                onChange={(e) => setShowBoxes(e.target.checked)}
              />
              Show Highlights ({pageAnchors.length})
            </label>
            <div className="spatial-legend">
              <span className="legend-item"><span className="legend-dot green"></span> Verified</span>
              <span className="legend-item"><span className="legend-dot amber"></span> Candidate</span>
              <span className="legend-item"><span className="legend-dot cyan"></span> Target</span>
            </div>
          </div>
        </div>

        {/* Main Body: Canvas + Inspector Sidebar */}
        <div className="spatial-viewer-body">
          {/* Document Canvas / Viewport */}
          <div className="spatial-viewer-viewport">
            {loadingManifest && (
              <div className="spatial-viewer-message">Loading spatial manifest…</div>
            )}
            {manifestError && (
              <div className="spatial-viewer-message error">{manifestError}</div>
            )}

            {!loadingManifest && !manifestError && (
              <div
                className="spatial-canvas-container"
                style={{ width: `${zoom}%`, margin: '0 auto' }}
              >
                {loadingImage && (
                  <div className="spatial-viewer-message">Rendering page {currentPage} at 200 DPI…</div>
                )}
                {imageError && (
                  <div className="spatial-viewer-message error">
                    Page render error: {imageError}
                  </div>
                )}

                {imageUrl && !imageError && (
                  <div className="spatial-page-frame" ref={pageContainerRef}>
                    <img
                      src={imageUrl}
                      alt={`Page ${currentPage} of ${manifest?.fileName}`}
                      className="spatial-page-img"
                    />

                    {/* Bounding Box Highlights Overlay Layer */}
                    {showBoxes && (
                      <div className="spatial-overlay-layer">
                        {pageAnchors.map((anchor) => {
                          const leftPct = (anchor.bboxX / anchor.pageImageWidth) * 100;
                          const topPct = (anchor.bboxY / anchor.pageImageHeight) * 100;
                          const widthPct = (anchor.bboxWidth / anchor.pageImageWidth) * 100;
                          const heightPct = (anchor.bboxHeight / anchor.pageImageHeight) * 100;

                          const isHovered = hoveredAnchor?.candidateFactId === anchor.candidateFactId;
                          const isSelected = selectedAnchor?.candidateFactId === anchor.candidateFactId;

                          return (
                            <div
                              key={anchor.candidateFactId}
                              className={getAnchorClassName(anchor)}
                              style={{
                                left: `${leftPct}%`,
                                top: `${topPct}%`,
                                width: `${widthPct}%`,
                                height: `${heightPct}%`,
                              }}
                              onClick={() => {
                                setSelectedAnchor(anchor);
                                setSidebarOpen(true);
                              }}
                              onMouseEnter={() => setHoveredAnchor(anchor)}
                              onMouseLeave={() => setHoveredAnchor(null)}
                            >
                              <div className="spatial-bbox-badge">
                                {anchor.isVerified ? '✓' : '●'} {anchor.label}
                              </div>

                              {/* Hover Tooltip */}
                              {isHovered && !isSelected && (
                                <div className="spatial-bbox-tooltip">
                                  <div className="tooltip-title">{anchor.label}</div>
                                  <div className="tooltip-value">
                                    {anchor.value} {anchor.period ? `(${anchor.period})` : ''}
                                  </div>
                                  <div className="tooltip-meta">
                                    <span className={`status-pill ${anchor.isVerified ? 'verified' : anchor.status.toLowerCase()}`}>
                                      {anchor.isVerified ? 'VERIFIED' : anchor.status}
                                    </span>
                                    <span>{humanize(anchor.source)}</span>
                                  </div>
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Collapsible Inspector Sidebar */}
          {sidebarOpen && (
            <aside className="spatial-viewer-sidebar">
              <div className="spatial-sidebar-tabs">
                <button
                  type="button"
                  className={`tab-btn ${sidebarTab === 'page' ? 'active' : ''}`}
                  onClick={() => setSidebarTab('page')}
                >
                  Page {currentPage} ({pageAnchors.length})
                </button>
                <button
                  type="button"
                  className={`tab-btn ${sidebarTab === 'all' ? 'active' : ''}`}
                  onClick={() => setSidebarTab('all')}
                >
                  All ({allAnchors.length})
                </button>
                <button
                  type="button"
                  className={`tab-btn ${sidebarTab === 'facts' ? 'active' : ''}`}
                  onClick={() => setSidebarTab('facts')}
                >
                  Linked Facts ({linkedFacts.length})
                </button>
              </div>

              {/* Sidebar Content */}
              <div className="spatial-sidebar-content">
                {/* Selected Anchor Inspector / Quick Review */}
                {selectedAnchor && (
                  <div className="selected-anchor-card">
                    <div className="selected-anchor-header">
                      <h4>Anchor Details</h4>
                      <button
                        type="button"
                        className="link-button"
                        onClick={() => setSelectedAnchor(null)}
                      >
                        Deselect
                      </button>
                    </div>

                    <div className="detail-grid">
                      <div><strong>Label</strong><span>{selectedAnchor.label}</span></div>
                      <div><strong>Extracted Value</strong><span>{selectedAnchor.value}</span></div>
                      <div><strong>Page</strong><span>{selectedAnchor.pageNumber}</span></div>
                      <div><strong>Source</strong><span>{humanize(selectedAnchor.source)}</span></div>
                      <div>
                        <strong>Status</strong>
                        <span className={`status-pill ${selectedAnchor.isVerified ? 'verified' : selectedAnchor.status.toLowerCase()}`}>
                          {selectedAnchor.isVerified ? 'VERIFIED' : selectedAnchor.status}
                        </span>
                      </div>
                      {selectedAnchor.factKey && (
                        <div><strong>Fact Key</strong><code>{selectedAnchor.factKey}</code></div>
                      )}
                    </div>

                    {/* Quick Review actions for PENDING candidates */}
                    {selectedAnchor.status === 'PENDING' && (
                      <div className="spatial-review-box">
                        <div className="eyebrow">Review Candidate Fact</div>
                        <label>
                          Label
                          <input
                            value={reviewLabel}
                            onChange={(e) => setReviewLabel(e.target.value)}
                          />
                        </label>
                        <label>
                          Value
                          <input
                            value={reviewValue}
                            onChange={(e) => setReviewValue(e.target.value)}
                          />
                        </label>
                        <label>
                          Period
                          <input
                            value={reviewPeriod}
                            onChange={(e) => setReviewPeriod(e.target.value)}
                            placeholder="e.g. FY2026"
                          />
                        </label>
                        <label>
                          Review Note
                          <input
                            value={reviewNote}
                            onChange={(e) => setReviewNote(e.target.value)}
                            placeholder="Optional verification comment"
                          />
                        </label>

                        {actionError && <div className="error-text">{actionError}</div>}

                        <div className="spatial-review-actions">
                          <button
                            type="button"
                            className="primary"
                            disabled={actionLoading}
                            onClick={handleAccept}
                          >
                            {actionLoading ? 'Saving...' : 'Accept Fact'}
                          </button>
                          <button
                            type="button"
                            className="secondary danger-btn"
                            disabled={actionLoading}
                            onClick={handleReject}
                          >
                            Reject
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {/* Tab: Page Anchors */}
                {sidebarTab === 'page' && (
                  <div className="anchors-list">
                    {pageAnchors.length === 0 && (
                      <p className="hint">No spatial anchors on page {currentPage}.</p>
                    )}
                    {pageAnchors.map((anchor) => (
                      <div
                        key={anchor.candidateFactId}
                        className={`anchor-item ${selectedAnchor?.candidateFactId === anchor.candidateFactId ? 'selected' : ''}`}
                        onClick={() => setSelectedAnchor(anchor)}
                      >
                        <div className="anchor-item-main">
                          <strong>{anchor.label}</strong>
                          <span className="anchor-item-val">{anchor.value}</span>
                        </div>
                        <div className="anchor-item-meta">
                          <span className={`status-pill ${anchor.isVerified ? 'verified' : anchor.status.toLowerCase()}`}>
                            {anchor.isVerified ? 'VERIFIED' : anchor.status}
                          </span>
                          <span className="hint">{humanize(anchor.source)}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                {/* Tab: All Document Anchors */}
                {sidebarTab === 'all' && (
                  <div className="anchors-list">
                    {allAnchors.length === 0 && (
                      <p className="hint">No anchors extracted for this document.</p>
                    )}
                    {allAnchors.map((anchor) => (
                      <div
                        key={anchor.candidateFactId}
                        className={`anchor-item ${selectedAnchor?.candidateFactId === anchor.candidateFactId ? 'selected' : ''}`}
                        onClick={() => {
                          setCurrentPage(anchor.pageNumber);
                          setSelectedAnchor(anchor);
                        }}
                      >
                        <div className="anchor-item-main">
                          <strong>{anchor.label}</strong>
                          <span className="anchor-item-val">{anchor.value}</span>
                        </div>
                        <div className="anchor-item-meta">
                          <span className="page-pill">Page {anchor.pageNumber}</span>
                          <span className={`status-pill ${anchor.isVerified ? 'verified' : anchor.status.toLowerCase()}`}>
                            {anchor.isVerified ? 'VERIFIED' : anchor.status}
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                {/* Tab: Linked Facts */}
                {sidebarTab === 'facts' && (
                  <div className="anchors-list">
                    {linkedFacts.length === 0 && (
                      <p className="hint">No facts linked to this document yet.</p>
                    )}
                    {linkedFacts.map((fact) => (
                      <div
                        key={fact.id}
                        className={`anchor-item ${targetFactId === fact.id ? 'selected' : ''}`}
                        onClick={() => {
                          if (fact.pageNumber) {
                            setCurrentPage(fact.pageNumber);
                            const matching = allAnchors.find((a) => a.resultingFactId === fact.id);
                            if (matching) setSelectedAnchor(matching);
                          }
                        }}
                      >
                        <div className="anchor-item-main">
                          <strong>{fact.label}</strong>
                          <span className="anchor-item-val">{fact.value} {fact.unit}</span>
                        </div>
                        <div className="anchor-item-meta">
                          {fact.hasSpatialAnchor ? (
                            <span className="page-pill">Page {fact.pageNumber}</span>
                          ) : (
                            <span className="hint">No anchor</span>
                          )}
                          <span className={`status-pill ${fact.status === 'VERIFIED' ? 'verified' : ''}`}>
                            {fact.status}
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </aside>
          )}
        </div>
      </aside>
    </div>
  );
}
