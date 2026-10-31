import { useState, useEffect } from 'react';
import toast from 'react-hot-toast';
import * as capTableApi from '../api/captable';

export default function CapTableWorkbench({ transactionId }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeSubTab, setActiveSubTab] = useState('holdings'); // holdings, lockins, actions, ipo
  const [asOfDate, setAsOfDate] = useState('');
  const [recalculating, setRecalculating] = useState(false);

  // Modal / Form states
  const [showIpoModal, setShowIpoModal] = useState(false);
  const [ipoForm, setIpoForm] = useState({
    freshIssueShares: '',
    ofsShares: '',
    priceBandLower: '',
    priceBandUpper: '',
    expectedAllotmentDate: ''
  });

  const loadCapTable = async (asOf = asOfDate) => {
    try {
      setLoading(true);
      const res = await capTableApi.getCapTable(transactionId, asOf || null);
      setData(res);
      if (res.ipoParameters) {
        setIpoForm({
          freshIssueShares: res.ipoParameters.freshIssueShares || '',
          ofsShares: res.ipoParameters.ofsShares || '',
          priceBandLower: res.ipoParameters.priceBandLower || '',
          priceBandUpper: res.ipoParameters.priceBandUpper || '',
          expectedAllotmentDate: res.ipoParameters.expectedAllotmentDate || ''
        });
      }
    } catch (err) {
      toast.error('Failed to load cap table data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCapTable();
  }, [transactionId]);

  const handleRecalculateLockIns = async () => {
    try {
      setRecalculating(true);
      await capTableApi.recalculateLockIns(transactionId, {
        allotmentDate: ipoForm.expectedAllotmentDate || null
      });
      toast.success('Lock-ins recalculated under SEBI ICDR 2025 Schedule');
      loadCapTable();
    } catch (err) {
      toast.error('Failed to recalculate lock-ins');
    } finally {
      setRecalculating(false);
    }
  };

  const handleSaveIpoParams = async (e) => {
    e.preventDefault();
    try {
      await capTableApi.updateIpoParameters(transactionId, {
        freshIssueShares: Number(ipoForm.freshIssueShares) || 0,
        ofsShares: Number(ipoForm.ofsShares) || 0,
        priceBandLower: ipoForm.priceBandLower ? Number(ipoForm.priceBandLower) : null,
        priceBandUpper: ipoForm.priceBandUpper ? Number(ipoForm.priceBandUpper) : null,
        expectedAllotmentDate: ipoForm.expectedAllotmentDate || null
      });
      toast.success('IPO issue parameters updated');
      setShowIpoModal(false);
      loadCapTable();
    } catch (err) {
      toast.error('Failed to update IPO parameters');
    }
  };

  if (loading && !data) {
    return <div className="card loading-placeholder">Loading Cap Table Engine...</div>;
  }

  const formatNumber = (num) => {
    if (num === null || num === undefined) return '0';
    return Number(num).toLocaleString('en-IN');
  };

  const formatCurrency = (num) => {
    if (!num) return '₹0';
    const val = Number(num);
    if (val >= 10000000) {
      return `₹${(val / 10000000).toFixed(2)} Cr`;
    }
    return `₹${val.toLocaleString('en-IN')}`;
  };

  return (
    <div className="captable-workbench">
      {/* Top Header & Historical Date Filter */}
      <div className="workbench-header flex justify-between items-center mb-4">
        <div>
          <h2 className="text-xl font-bold">Cap Table & Dilution Engine</h2>
          <p className="text-sm text-secondary">
            Continuous point-in-time holdings replay, fully diluted calculation & SEBI ICDR 2025 tiered lock-in
          </p>
        </div>
        <div className="flex items-center gap-2">
          <label className="text-sm font-medium">As Of:</label>
          <input
            type="date"
            className="input-field text-sm"
            value={asOfDate}
            onChange={(e) => {
              setAsOfDate(e.target.value);
              loadCapTable(e.target.value);
            }}
          />
          {asOfDate && (
            <button
              className="btn btn-secondary text-xs"
              onClick={() => {
                setAsOfDate('');
                loadCapTable('');
              }}
            >
              Reset to Current
            </button>
          )}
        </div>
      </div>

      {/* Fact Divergence Alert (Ticket B6 Invariant) */}
      {data?.conflicts && data.conflicts.length > 0 && (
        <div className="alert alert-warning mb-4">
          <div className="font-semibold flex items-center gap-2">
            <span>⚠️</span> Divergence Detected Against Active Facts
          </div>
          <div className="text-sm mt-1">
            {data.conflicts.map((c, i) => (
              <div key={i} className="mt-1">
                <span className="font-mono text-xs bg-warning-light px-1 py-0.5 rounded">{c.factKey}</span>: {c.description}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Metric Cards Banner */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        <div className="stat-card">
          <div className="stat-label">Total Equity Shares (Basic)</div>
          <div className="stat-value">{formatNumber(data?.totalBasicShares)}</div>
          <div className="stat-sub">Pre-issue: {formatCurrency(data?.preIssuePaidUpCapitalInr)}</div>
        </div>

        <div className="stat-card">
          <div className="stat-label">Fully Diluted Shares</div>
          <div className="stat-value">{formatNumber(data?.totalDilutedShares)}</div>
          <div className="stat-sub">Includes CCPS, CCDs & Options</div>
        </div>

        <div className="stat-card">
          <div className="stat-label">Post-Issue Capital</div>
          <div className="stat-value">{formatNumber(data?.postIssueShares)}</div>
          <div className="stat-sub">Fresh: +{formatNumber(data?.freshIssueShares)} | OFS: {formatNumber(data?.ofsShares)}</div>
        </div>

        <div className="stat-card">
          <div className="stat-label">Promoter Post-Issue Holding</div>
          <div className="stat-value flex items-center gap-2">
            <span>{Number(data?.promoterPostIssuePct || 0).toFixed(2)}%</span>
            {data?.isMpcSatisfied ? (
              <span className="badge badge-complete text-xs">20% MPC Met</span>
            ) : (
              <span className="badge badge-failed text-xs">MPC Shortfall</span>
            )}
          </div>
          <div className="stat-sub">
            {formatNumber(data?.promoterPostIssueShares)} shares (Min 20% MPC: {formatNumber(data?.mpcRequiredShares)})
          </div>
        </div>
      </div>

      {/* Navigation Sub-Tabs */}
      <div className="tabs-nav border-b border-divider mb-4 flex gap-4">
        <button
          className={`tab-btn pb-2 font-medium ${activeSubTab === 'holdings' ? 'active-tab border-b-2 border-primary text-primary' : 'text-secondary'}`}
          onClick={() => setActiveSubTab('holdings')}
        >
          Shareholding Pattern ({data?.holdings?.length || 0})
        </button>
        <button
          className={`tab-btn pb-2 font-medium ${activeSubTab === 'lockins' ? 'active-tab border-b-2 border-primary text-primary' : 'text-secondary'}`}
          onClick={() => setActiveSubTab('lockins')}
        >
          SEBI Lock-In Schedule ({data?.lockIns?.length || 0})
        </button>
        <button
          className={`tab-btn pb-2 font-medium ${activeSubTab === 'actions' ? 'active-tab border-b-2 border-primary text-primary' : 'text-secondary'}`}
          onClick={() => setActiveSubTab('actions')}
        >
          Corporate Actions ({data?.transactions?.length || 0})
        </button>
        <button
          className={`tab-btn pb-2 font-medium ${activeSubTab === 'ipo' ? 'active-tab border-b-2 border-primary text-primary' : 'text-secondary'}`}
          onClick={() => setActiveSubTab('ipo')}
        >
          IPO Issue Parameters
        </button>
      </div>

      {/* Sub-Tab 1: Shareholding Pattern Table */}
      {activeSubTab === 'holdings' && (
        <div className="card overflow-x-auto">
          <table className="data-table w-full text-left text-sm">
            <thead>
              <tr className="border-b bg-surface-subtle">
                <th className="p-3">Shareholder / Entity</th>
                <th className="p-3">Category</th>
                <th className="p-3 text-right">Basic Shares</th>
                <th className="p-3 text-right">Basic %</th>
                <th className="p-3 text-right">Diluted Shares</th>
                <th className="p-3 text-right">Diluted %</th>
                <th className="p-3 text-right">Post-Issue Shares</th>
                <th className="p-3 text-right">Post-Issue %</th>
                <th className="p-3 text-right">Locked</th>
                <th className="p-3 text-right">Encumbered</th>
              </tr>
            </thead>
            <tbody>
              {data?.holdings && data.holdings.length > 0 ? (
                data.holdings.map((h) => (
                  <tr key={h.holderId} className="border-b hover:bg-surface-hover">
                    <td className="p-3 font-semibold">{h.holderName}</td>
                    <td className="p-3">
                      <span className={`badge ${h.category.includes('PROMOTER') ? 'badge-primary' : 'badge-neutral'} text-xs`}>
                        {h.category}
                      </span>
                    </td>
                    <td className="p-3 text-right font-mono">{formatNumber(h.basicShares)}</td>
                    <td className="p-3 text-right font-mono">{Number(h.basicOwnershipPct).toFixed(2)}%</td>
                    <td className="p-3 text-right font-mono text-secondary">{formatNumber(h.dilutedShares)}</td>
                    <td className="p-3 text-right font-mono text-secondary">{Number(h.dilutedOwnershipPct).toFixed(2)}%</td>
                    <td className="p-3 text-right font-mono font-semibold">{formatNumber(h.postIssueShares)}</td>
                    <td className="p-3 text-right font-mono font-semibold">{Number(h.postIssueOwnershipPct).toFixed(2)}%</td>
                    <td className="p-3 text-right font-mono text-warning">{formatNumber(h.lockedShares)}</td>
                    <td className="p-3 text-right font-mono text-danger">{formatNumber(h.encumberedShares)}</td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan="10" className="p-6 text-center text-secondary">
                    No shareholding data recorded for this transaction.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Sub-Tab 2: SEBI ICDR 2025 Tiered Lock-In Schedule */}
      {activeSubTab === 'lockins' && (
        <div className="card">
          <div className="bg-surface-subtle p-4 rounded mb-4 border border-border">
            <div className="flex justify-between items-center">
              <div>
                <h3 className="font-semibold text-sm">SEBI ICDR 2025 Tiered Lock-in Schedule</h3>
                <p className="text-xs text-secondary mt-1">
                  Regulation 238 (2025 Amendment): 20% MPC locked for 3 years; excess holding tiered: 50% for 2 years, remaining 50% for 1 year.
                  Regulation 239: Pre-issue non-promoter equity locked for 1 year with statutory exemptions.
                </p>
              </div>
              <button
                className="btn btn-primary text-xs"
                onClick={handleRecalculateLockIns}
                disabled={recalculating}
              >
                {recalculating ? 'Calculating...' : 'Recalculate Lock-ins'}
              </button>
            </div>
          </div>

          <table className="data-table w-full text-left text-sm">
            <thead>
              <tr className="border-b bg-surface-subtle">
                <th className="p-3">Holder</th>
                <th className="p-3">Lock-in Tier</th>
                <th className="p-3 text-right">Locked Shares</th>
                <th className="p-3">Start Date</th>
                <th className="p-3">Expiry Date</th>
                <th className="p-3">Statutory Basis</th>
                <th className="p-3">Exemption</th>
              </tr>
            </thead>
            <tbody>
              {data?.lockIns && data.lockIns.length > 0 ? (
                data.lockIns.map((l) => (
                  <tr key={l.id} className="border-b hover:bg-surface-hover">
                    <td className="p-3 font-semibold">{l.holderName}</td>
                    <td className="p-3">
                      <span className={`badge ${l.lockInCategory.includes('3YR') ? 'badge-primary' : l.lockInCategory.includes('2YR') ? 'badge-warning' : 'badge-neutral'} text-xs`}>
                        {l.lockInCategory}
                      </span>
                    </td>
                    <td className="p-3 text-right font-mono font-semibold">{formatNumber(l.lockedShareCount)}</td>
                    <td className="p-3">{l.startDate}</td>
                    <td className="p-3 font-semibold">{l.expiryDate}</td>
                    <td className="p-3 text-xs">{l.statutory ? 'SEBI ICDR Mandate' : 'Contractual'}</td>
                    <td className="p-3">
                      {l.exemptionClaimed && l.exemptionClaimed !== 'NONE' ? (
                        <span className="badge badge-complete text-xs">{l.exemptionClaimed}</span>
                      ) : (
                        <span className="text-secondary text-xs">None</span>
                      )}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan="7" className="p-6 text-center text-secondary">
                    No lock-in entries generated. Click "Recalculate Lock-ins" to apply SEBI ICDR 2025 Schedule.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Sub-Tab 3: Corporate Actions Timeline */}
      {activeSubTab === 'actions' && (
        <div className="card">
          <div className="flex justify-between items-center mb-3">
            <h3 className="font-semibold text-sm">Corporate Actions & Share Capital History</h3>
          </div>
          <table className="data-table w-full text-left text-sm">
            <thead>
              <tr className="border-b bg-surface-subtle">
                <th className="p-3">Event Date</th>
                <th className="p-3">Event Type</th>
                <th className="p-3">Security</th>
                <th className="p-3">From</th>
                <th className="p-3">To</th>
                <th className="p-3 text-right">Shares</th>
                <th className="p-3 text-right">Price / Share</th>
                <th className="p-3">Consideration</th>
                <th className="p-3">ROC Filing Ref</th>
              </tr>
            </thead>
            <tbody>
              {data?.transactions && data.transactions.length > 0 ? (
                data.transactions.map((t) => (
                  <tr key={t.id} className="border-b hover:bg-surface-hover">
                    <td className="p-3 font-mono">{t.eventDate}</td>
                    <td className="p-3">
                      <span className="badge badge-primary text-xs">{t.eventType}</span>
                    </td>
                    <td className="p-3">{t.securityClassName}</td>
                    <td className="p-3 text-secondary">{t.sourceHolderName || '— (Treasury)'}</td>
                    <td className="p-3 font-semibold">{t.destHolderName || '— (Cancellation)'}</td>
                    <td className="p-3 text-right font-mono font-semibold">{formatNumber(t.shareCount)}</td>
                    <td className="p-3 text-right font-mono">₹{Number(t.pricePerShare).toFixed(2)}</td>
                    <td className="p-3 text-xs">{t.considerationType}</td>
                    <td className="p-3 font-mono text-xs text-secondary">{t.rocFilingRef || 'PAS-3 / MGT-14'}</td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan="9" className="p-6 text-center text-secondary">
                    No corporate actions recorded.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Sub-Tab 4: IPO Issue Parameters */}
      {activeSubTab === 'ipo' && (
        <div className="card max-w-2xl">
          <h3 className="font-semibold text-base mb-4">IPO Capital Structure Parameters</h3>
          <form onSubmit={handleSaveIpoParams} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-secondary mb-1">Fresh Issue Shares</label>
                <input
                  type="number"
                  className="input-field w-full text-sm font-mono"
                  placeholder="e.g. 2000000"
                  value={ipoForm.freshIssueShares}
                  onChange={(e) => setIpoForm({ ...ipoForm, freshIssueShares: e.target.value })}
                />
                <span className="text-xs text-secondary">Increases post-issue share capital</span>
              </div>

              <div>
                <label className="block text-xs font-medium text-secondary mb-1">Offer for Sale (OFS) Shares</label>
                <input
                  type="number"
                  className="input-field w-full text-sm font-mono"
                  placeholder="e.g. 1000000"
                  value={ipoForm.ofsShares}
                  onChange={(e) => setIpoForm({ ...ipoForm, ofsShares: e.target.value })}
                />
                <span className="text-xs text-secondary">Secondary sale by existing holders</span>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-secondary mb-1">Price Band Lower (₹)</label>
                <input
                  type="number"
                  step="0.01"
                  className="input-field w-full text-sm font-mono"
                  placeholder="e.g. 120.00"
                  value={ipoForm.priceBandLower}
                  onChange={(e) => setIpoForm({ ...ipoForm, priceBandLower: e.target.value })}
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-secondary mb-1">Price Band Upper (₹)</label>
                <input
                  type="number"
                  step="0.01"
                  className="input-field w-full text-sm font-mono"
                  placeholder="e.g. 130.00"
                  value={ipoForm.priceBandUpper}
                  onChange={(e) => setIpoForm({ ...ipoForm, priceBandUpper: e.target.value })}
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-secondary mb-1">Expected Allotment Date</label>
              <input
                type="date"
                className="input-field w-full text-sm"
                value={ipoForm.expectedAllotmentDate}
                onChange={(e) => setIpoForm({ ...ipoForm, expectedAllotmentDate: e.target.value })}
              />
              <span className="text-xs text-secondary">Used as the statutory anchor date for SEBI lock-in schedules</span>
            </div>

            <div className="pt-2 flex justify-end">
              <button type="submit" className="btn btn-primary">
                Save Issue Parameters
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
