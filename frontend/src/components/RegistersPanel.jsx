import { useState, useEffect } from 'react';
import toast from 'react-hot-toast';
import * as registersApi from '../api/registers';

export default function RegistersPanel({ transactionId, workstreamId }) {
  const [activeTab, setActiveTab] = useState('rpt'); // rpt, litigation, contracts
  const [rptData, setRptData] = useState(null);
  const [litData, setLitData] = useState(null);
  const [contractData, setContractData] = useState(null);
  const [loading, setLoading] = useState(true);

  // Modal states
  const [showPartyModal, setShowPartyModal] = useState(false);
  const [showRptTxModal, setShowRptTxModal] = useState(false);
  const [showLitModal, setShowLitModal] = useState(false);
  const [showContractModal, setShowContractModal] = useState(false);

  // Form states
  const [partyForm, setPartyForm] = useState({
    name: '',
    partyType: 'BODY_CORPORATE',
    pan: '',
    dinCin: '',
    relationshipNature: 'PROMOTER',
    relativeDetails: '',
    effectiveFrom: new Date().toISOString().split('T')[0]
  });

  const [rptTxForm, setRptTxForm] = useState({
    relatedPartyId: '',
    financialYear: 'FY2025-26',
    transactionType: 'SALE_OF_GOODS',
    amount: '',
    inOrdinaryCourse: true,
    isArmsLength: true,
    armLengthBasis: '',
    auditCommitteeApproval: 'OMNIBUS'
  });

  const [litForm, setLitForm] = useState({
    caseNumber: '',
    courtForum: '',
    jurisdictionCity: '',
    caseTitle: '',
    category: 'CRIMINAL_PROCEEDING',
    entityType: 'ISSUER',
    entityName: '',
    partyRole: 'RESPONDENT_DEFENDANT',
    oppositeParty: '',
    claimedAmount: '',
    contingentLiabilityExposure: '',
    potentialImpact: 'FINANCIAL',
    caseSummary: '',
    status: 'PENDING'
  });

  const [contractForm, setContractForm] = useState({
    contractReference: '',
    title: '',
    contractCategory: 'KEY_CUSTOMER_CONTRACT',
    counterpartyName: '',
    executionDate: new Date().toISOString().split('T')[0],
    effectiveDate: new Date().toISOString().split('T')[0],
    contractValueAnnual: '',
    revenuePercentage: '',
    supplyPercentage: '',
    hasChangeOfControl: false,
    changeOfControlConsentObtained: false,
    hasExclusivity: false
  });

  const loadAll = async () => {
    try {
      setLoading(true);
      const [rpt, lit, contracts] = await Promise.all([
        registersApi.getRptSummary(transactionId),
        registersApi.getLitigationSummary(transactionId),
        registersApi.getContractSummary(transactionId)
      ]);
      setRptData(rpt);
      setLitData(lit);
      setContractData(contracts);
    } catch (err) {
      toast.error('Failed to load domain registers');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAll();
  }, [transactionId]);

  const handleCreateParty = async (e) => {
    e.preventDefault();
    try {
      await registersApi.createRelatedParty(transactionId, partyForm);
      toast.success('Related party recorded');
      setShowPartyModal(false);
      loadAll();
    } catch (err) {
      toast.error('Failed to record related party');
    }
  };

  const handleRecordRptTx = async (e) => {
    e.preventDefault();
    try {
      await registersApi.recordRptTransaction(transactionId, {
        ...rptTxForm,
        amount: Number(rptTxForm.amount)
      });
      toast.success('RPT transaction recorded & materiality evaluated');
      setShowRptTxModal(false);
      loadAll();
    } catch (err) {
      toast.error('Failed to record transaction');
    }
  };

  const handleRecordLitigation = async (e) => {
    e.preventDefault();
    try {
      await registersApi.recordLitigationCase(transactionId, {
        ...litForm,
        workstreamId: workstreamId || null,
        claimedAmount: Number(litForm.claimedAmount) || 0,
        contingentLiabilityExposure: Number(litForm.contingentLiabilityExposure) || 0
      });
      toast.success('Litigation case recorded & materiality evaluated');
      setShowLitModal(false);
      loadAll();
    } catch (err) {
      toast.error('Failed to record litigation case');
    }
  };

  const handleRecordContract = async (e) => {
    e.preventDefault();
    try {
      await registersApi.recordContract(transactionId, {
        ...contractForm,
        workstreamId: workstreamId || null,
        contractValueAnnual: contractForm.contractValueAnnual ? Number(contractForm.contractValueAnnual) : null,
        revenuePercentage: contractForm.revenuePercentage ? Number(contractForm.revenuePercentage) : null,
        supplyPercentage: contractForm.supplyPercentage ? Number(contractForm.supplyPercentage) : null
      });
      toast.success('Material contract recorded');
      setShowContractModal(false);
      loadAll();
    } catch (err) {
      toast.error('Failed to record contract');
    }
  };

  if (loading && !rptData) {
    return <div className="card loading-placeholder">Loading Governance Registers...</div>;
  }

  const formatCurrency = (num) => {
    if (!num) return '₹0';
    const val = Number(num);
    if (val >= 10000000) {
      return `₹${(val / 10000000).toFixed(2)} Cr`;
    }
    return `₹${val.toLocaleString('en-IN')}`;
  };

  return (
    <div className="registers-panel">
      {/* Header and Sub-Tab Navigation */}
      <div className="flex justify-between items-center mb-4">
        <div>
          <h2 className="text-xl font-bold">Transaction Governance & Legal Registers</h2>
          <p className="text-sm text-secondary">
            Statutory RPT ledger, Litigation cases register & Material commercial contracts screening
          </p>
        </div>
      </div>

      <div className="tabs-nav border-b border-divider mb-6 flex gap-6">
        <button
          className={`tab-btn pb-2 font-medium ${activeTab === 'rpt' ? 'border-b-2 border-primary text-primary font-semibold' : 'text-secondary'}`}
          onClick={() => setActiveTab('rpt')}
        >
          Related Party Transactions ({rptData?.transactions?.length || 0})
        </button>
        <button
          className={`tab-btn pb-2 font-medium ${activeTab === 'litigation' ? 'border-b-2 border-primary text-primary font-semibold' : 'text-secondary'}`}
          onClick={() => setActiveTab('litigation')}
        >
          Litigation & Disputes ({litData?.cases?.length || 0})
        </button>
        <button
          className={`tab-btn pb-2 font-medium ${activeTab === 'contracts' ? 'border-b-2 border-primary text-primary font-semibold' : 'text-secondary'}`}
          onClick={() => setActiveTab('contracts')}
        >
          Material Commercial Contracts ({contractData?.contracts?.length || 0})
        </button>
      </div>

      {/* TAB 1: RELATED PARTY TRANSACTIONS */}
      {activeTab === 'rpt' && (
        <div>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
            <div className="stat-card">
              <div className="stat-label">Total RPT Exposure</div>
              <div className="stat-value font-mono">{formatCurrency(rptData?.totalRptExposure)}</div>
              <div className="stat-sub">{rptData?.totalTransactionCount || 0} Transactions across {rptData?.parties?.length || 0} Parties</div>
            </div>

            <div className="stat-card">
              <div className="stat-label">Material RPTs</div>
              <div className="stat-value text-warning">{rptData?.materialTransactionCount || 0}</div>
              <div className="stat-sub">Under SEBI LODR Reg 23 / CA 2013 §188</div>
            </div>

            <div className="stat-card">
              <div className="stat-label">Unapproved Transactions</div>
              <div className="stat-value text-danger">{rptData?.unapprovedTransactionCount || 0}</div>
              <div className="stat-sub">Lacking Audit Committee / Shareholder Approval</div>
            </div>

            <div className="stat-card flex flex-col justify-center items-end">
              <div className="flex gap-2">
                <button className="btn btn-secondary text-xs" onClick={() => setShowPartyModal(true)}>
                  + Add Party
                </button>
                <button className="btn btn-primary text-xs" onClick={() => setShowRptTxModal(true)}>
                  + Record Transaction
                </button>
              </div>
            </div>
          </div>

          {/* Compliance Alerts */}
          {rptData?.complianceIssues && rptData.complianceIssues.length > 0 && (
            <div className="alert alert-warning mb-4">
              <div className="font-semibold text-sm">RPT Governance Alerts:</div>
              <ul className="list-disc pl-5 text-xs mt-1">
                {rptData.complianceIssues.map((issue, idx) => (
                  <li key={idx}>{issue}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Transactions Table */}
          <div className="card">
            <h3 className="font-semibold text-sm mb-3">RPT Transaction Ledger</h3>
            <table className="data-table w-full text-left text-sm">
              <thead>
                <tr className="border-b bg-surface-subtle">
                  <th className="p-3">Related Party</th>
                  <th className="p-3">Relation</th>
                  <th className="p-3">Period</th>
                  <th className="p-3">Type</th>
                  <th className="p-3 text-right">Amount</th>
                  <th className="p-3 text-center">Ordinary Course & Arm's Length</th>
                  <th className="p-3 text-center">Audit Committee</th>
                  <th className="p-3 text-center">Statutory Materiality</th>
                </tr>
              </thead>
              <tbody>
                {rptData?.transactions && rptData.transactions.length > 0 ? (
                  rptData.transactions.map((tx) => (
                    <tr key={tx.id} className="border-b hover:bg-surface-hover">
                      <td className="p-3 font-semibold">{tx.relatedPartyName}</td>
                      <td className="p-3">
                        <span className="badge badge-neutral text-xs">{tx.relationshipNature}</span>
                      </td>
                      <td className="p-3 font-mono text-xs">{tx.financialYear}</td>
                      <td className="p-3">{tx.transactionType}</td>
                      <td className="p-3 text-right font-mono font-semibold">{formatCurrency(tx.amount)}</td>
                      <td className="p-3 text-center">
                        {tx.inOrdinaryCourse && tx.isArmsLength ? (
                          <span className="badge badge-complete text-xs">Exempt (OC + AL)</span>
                        ) : (
                          <span className="badge badge-warning text-xs">Non-Exempt</span>
                        )}
                      </td>
                      <td className="p-3 text-center">
                        <span className={`badge ${tx.auditCommitteeApproval === 'NONE' ? 'badge-failed' : 'badge-complete'} text-xs`}>
                          {tx.auditCommitteeApproval}
                        </span>
                      </td>
                      <td className="p-3 text-center">
                        <div className="flex flex-col gap-1 items-center">
                          {tx.sebiLodrMaterial && (
                            <span className="badge badge-warning text-xs">SEBI LODR Reg 23 Material</span>
                          )}
                          {tx.ca2013Sec188Applicable && (
                            <span className="badge badge-failed text-xs">CA §188 Approval Required</span>
                          )}
                          {!tx.sebiLodrMaterial && !tx.ca2013Sec188Applicable && (
                            <span className="badge badge-neutral text-xs">Below Thresholds</span>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan="8" className="p-6 text-center text-secondary">
                      No related party transactions recorded.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 2: LITIGATION & DISPUTES */}
      {activeTab === 'litigation' && (
        <div>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
            <div className="stat-card">
              <div className="stat-label">Total Claimed Amount</div>
              <div className="stat-value font-mono">{formatCurrency(litData?.totalClaimedAmount)}</div>
              <div className="stat-sub">{litData?.totalCasesCount || 0} Total Legal Proceedings</div>
            </div>

            <div className="stat-card">
              <div className="stat-label">Contingent Liability</div>
              <div className="stat-value font-mono text-warning">{formatCurrency(litData?.totalContingentLiability)}</div>
              <div className="stat-sub">Disclosed in notes to accounts</div>
            </div>

            <div className="stat-card">
              <div className="stat-label">Pending Criminal Proceedings</div>
              <div className="stat-value text-danger">{litData?.pendingCriminalCount || 0}</div>
              <div className="stat-sub">Mandatory SEBI ICDR Schedule VI Blocker</div>
            </div>

            <div className="stat-card flex flex-col justify-center items-end">
              <button className="btn btn-primary text-xs" onClick={() => setShowLitModal(true)}>
                + Record Litigation Case
              </button>
            </div>
          </div>

          {/* Blockers Alert */}
          {litData?.diligenceBlockers && litData.diligenceBlockers.length > 0 && (
            <div className="alert alert-danger mb-4">
              <div className="font-semibold text-sm">Critical Litigation Blockers:</div>
              <ul className="list-disc pl-5 text-xs mt-1">
                {litData.diligenceBlockers.map((b, idx) => (
                  <li key={idx}>{b}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Cases Table */}
          <div className="card">
            <table className="data-table w-full text-left text-sm">
              <thead>
                <tr className="border-b bg-surface-subtle">
                  <th className="p-3">Case Title & Forum</th>
                  <th className="p-3">Category</th>
                  <th className="p-3">Target Entity</th>
                  <th className="p-3 text-right">Exposure</th>
                  <th className="p-3">Risk Impact</th>
                  <th className="p-3">Status</th>
                  <th className="p-3">Materiality Basis</th>
                </tr>
              </thead>
              <tbody>
                {litData?.cases && litData.cases.length > 0 ? (
                  litData.cases.map((c) => (
                    <tr key={c.id} className="border-b hover:bg-surface-hover">
                      <td className="p-3">
                        <div className="font-semibold">{c.caseTitle}</div>
                        <div className="text-xs text-secondary font-mono">{c.caseNumber} • {c.courtForum}</div>
                      </td>
                      <td className="p-3">
                        <span className={`badge ${c.category.includes('CRIMINAL') ? 'badge-failed' : 'badge-neutral'} text-xs`}>
                          {c.category}
                        </span>
                      </td>
                      <td className="p-3 font-medium">{c.entityName} ({c.entityType})</td>
                      <td className="p-3 text-right font-mono font-semibold">{formatCurrency(c.claimedAmount)}</td>
                      <td className="p-3">
                        <span className="text-xs font-medium">{c.potentialImpact}</span>
                      </td>
                      <td className="p-3">
                        <span className={`badge ${c.status === 'PENDING' ? 'badge-warning' : 'badge-complete'} text-xs`}>
                          {c.status}
                        </span>
                      </td>
                      <td className="p-3">
                        {c.isMaterial ? (
                          <span className="badge badge-warning text-xs">{c.materialityBasis}</span>
                        ) : (
                          <span className="badge badge-neutral text-xs">Below Threshold</span>
                        )}
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan="7" className="p-6 text-center text-secondary">
                      No litigation cases recorded.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 3: MATERIAL COMMERCIAL CONTRACTS */}
      {activeTab === 'contracts' && (
        <div>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
            <div className="stat-card">
              <div className="stat-label">Material Contracts</div>
              <div className="stat-value">{contractData?.totalContractsCount || 0}</div>
              <div className="stat-sub">Annual Value: {formatCurrency(contractData?.totalAnnualValue)}</div>
            </div>

            <div className="stat-card">
              <div className="stat-label">&gt;10% Screening Concentration</div>
              <div className="stat-value text-warning">{contractData?.above10PctCount || 0}</div>
              <div className="stat-sub">Customers / Vendors &gt;10% of revenue/supply</div>
            </div>

            <div className="stat-card">
              <div className="stat-label">Pending Change of Control Consents</div>
              <div className="stat-value text-danger">{contractData?.changeOfControlPendingCount || 0}</div>
              <div className="stat-sub">Lender / Counterparty IPO Waivers Required</div>
            </div>

            <div className="stat-card flex flex-col justify-center items-end">
              <button className="btn btn-primary text-xs" onClick={() => setShowContractModal(true)}>
                + Record Contract
              </button>
            </div>
          </div>

          {/* Blockers Alert */}
          {contractData?.diligenceBlockers && contractData.diligenceBlockers.length > 0 && (
            <div className="alert alert-danger mb-4">
              <div className="font-semibold text-sm">Change of Control Covenants Pending:</div>
              <ul className="list-disc pl-5 text-xs mt-1">
                {contractData.diligenceBlockers.map((b, idx) => (
                  <li key={idx}>{b}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Contracts Table */}
          <div className="card">
            <table className="data-table w-full text-left text-sm">
              <thead>
                <tr className="border-b bg-surface-subtle">
                  <th className="p-3">Contract Title & Ref</th>
                  <th className="p-3">Category</th>
                  <th className="p-3">Counterparty</th>
                  <th className="p-3 text-right">Annual Value</th>
                  <th className="p-3 text-right">Concentration %</th>
                  <th className="p-3 text-center">Change of Control</th>
                  <th className="p-3 text-center">Exclusivity</th>
                  <th className="p-3">Status</th>
                </tr>
              </thead>
              <tbody>
                {contractData?.contracts && contractData.contracts.length > 0 ? (
                  contractData.contracts.map((ct) => (
                    <tr key={ct.id} className="border-b hover:bg-surface-hover">
                      <td className="p-3">
                        <div className="font-semibold">{ct.title}</div>
                        <div className="text-xs text-secondary font-mono">{ct.contractReference}</div>
                      </td>
                      <td className="p-3">
                        <span className="badge badge-neutral text-xs">{ct.contractCategory}</span>
                      </td>
                      <td className="p-3 font-medium">{ct.counterpartyName}</td>
                      <td className="p-3 text-right font-mono font-semibold">{formatCurrency(ct.contractValueAnnual)}</td>
                      <td className="p-3 text-right font-mono">
                        {ct.revenuePercentage ? `${ct.revenuePercentage}% Rev` : ct.supplyPercentage ? `${ct.supplyPercentage}% Supply` : '—'}
                        {ct.isAbove10PctScreeningIndicator && (
                          <span className="ml-1 badge badge-warning text-2xs">&gt;10% Screen</span>
                        )}
                      </td>
                      <td className="p-3 text-center">
                        {ct.hasChangeOfControl ? (
                          ct.changeOfControlConsentObtained ? (
                            <span className="badge badge-complete text-xs">Consent Obtained</span>
                          ) : (
                            <span className="badge badge-failed text-xs">Consent Pending</span>
                          )
                        ) : (
                          <span className="text-secondary text-xs">None</span>
                        )}
                      </td>
                      <td className="p-3 text-center">
                        {ct.hasExclusivity ? (
                          <span className="badge badge-warning text-xs">Exclusive</span>
                        ) : (
                          <span className="text-secondary text-xs">Non-exclusive</span>
                        )}
                      </td>
                      <td className="p-3">
                        <span className="badge badge-complete text-xs">{ct.status}</span>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan="8" className="p-6 text-center text-secondary">
                      No material commercial contracts recorded.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* MODAL 1: ADD RELATED PARTY */}
      {showPartyModal && (
        <div className="modal-backdrop">
          <div className="modal-content card max-w-lg">
            <h3 className="font-bold text-base mb-4">Add Related Party</h3>
            <form onSubmit={handleCreateParty} className="space-y-3">
              <div>
                <label className="text-xs text-secondary">Legal Name / Entity</label>
                <input
                  type="text"
                  required
                  className="input-field w-full text-sm"
                  value={partyForm.name}
                  onChange={(e) => setPartyForm({ ...partyForm, name: e.target.value })}
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-secondary">Party Type</label>
                  <select
                    className="input-field w-full text-sm"
                    value={partyForm.partyType}
                    onChange={(e) => setPartyForm({ ...partyForm, partyType: e.target.value })}
                  >
                    <option value="INDIVIDUAL">INDIVIDUAL</option>
                    <option value="BODY_CORPORATE">BODY_CORPORATE</option>
                    <option value="FIRM">FIRM</option>
                    <option value="HUF">HUF</option>
                    <option value="TRUST">TRUST</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs text-secondary">Relationship Nature</label>
                  <select
                    className="input-field w-full text-sm"
                    value={partyForm.relationshipNature}
                    onChange={(e) => setPartyForm({ ...partyForm, relationshipNature: e.target.value })}
                  >
                    <option value="PROMOTER">PROMOTER</option>
                    <option value="DIRECTOR">DIRECTOR</option>
                    <option value="KMP">KMP</option>
                    <option value="RELATIVE_OF_DIRECTOR_OR_KMP">RELATIVE_OF_DIRECTOR_OR_KMP</option>
                    <option value="ENTITY_WITH_SIGNIFICANT_INFLUENCE">ENTITY_WITH_SIGNIFICANT_INFLUENCE</option>
                    <option value="SUBSIDIARY">SUBSIDIARY</option>
                    <option value="ASSOCIATE_OR_JV">ASSOCIATE_OR_JV</option>
                  </select>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-secondary">PAN</label>
                  <input
                    type="text"
                    className="input-field w-full text-sm"
                    value={partyForm.pan}
                    onChange={(e) => setPartyForm({ ...partyForm, pan: e.target.value })}
                  />
                </div>
                <div>
                  <label className="text-xs text-secondary">DIN / CIN</label>
                  <input
                    type="text"
                    className="input-field w-full text-sm"
                    value={partyForm.dinCin}
                    onChange={(e) => setPartyForm({ ...partyForm, dinCin: e.target.value })}
                  />
                </div>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button type="button" className="btn btn-secondary text-sm" onClick={() => setShowPartyModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary text-sm">
                  Save Party
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 2: RECORD RPT TRANSACTION */}
      {showRptTxModal && (
        <div className="modal-backdrop">
          <div className="modal-content card max-w-lg">
            <h3 className="font-bold text-base mb-4">Record Related Party Transaction</h3>
            <form onSubmit={handleRecordRptTx} className="space-y-3">
              <div>
                <label className="text-xs text-secondary">Related Party</label>
                <select
                  required
                  className="input-field w-full text-sm"
                  value={rptTxForm.relatedPartyId}
                  onChange={(e) => setRptTxForm({ ...rptTxForm, relatedPartyId: e.target.value })}
                >
                  <option value="">Select Related Party</option>
                  {rptData?.parties?.map((p) => (
                    <option key={p.id} value={p.id}>{p.name} ({p.relationshipNature})</option>
                  ))}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-secondary">Financial Year</label>
                  <input
                    type="text"
                    className="input-field w-full text-sm"
                    value={rptTxForm.financialYear}
                    onChange={(e) => setRptTxForm({ ...rptTxForm, financialYear: e.target.value })}
                  />
                </div>
                <div>
                  <label className="text-xs text-secondary">Amount (₹)</label>
                  <input
                    type="number"
                    step="0.01"
                    required
                    className="input-field w-full text-sm font-mono"
                    value={rptTxForm.amount}
                    onChange={(e) => setRptTxForm({ ...rptTxForm, amount: e.target.value })}
                  />
                </div>
              </div>
              <div>
                <label className="text-xs text-secondary">Transaction Type</label>
                <select
                  className="input-field w-full text-sm"
                  value={rptTxForm.transactionType}
                  onChange={(e) => setRptTxForm({ ...rptTxForm, transactionType: e.target.value })}
                >
                  <option value="SALE_OF_GOODS">SALE_OF_GOODS</option>
                  <option value="PURCHASE_OF_GOODS">PURCHASE_OF_GOODS</option>
                  <option value="RENDERING_OF_SERVICES">RENDERING_OF_SERVICES</option>
                  <option value="RECEIVING_OF_SERVICES">RECEIVING_OF_SERVICES</option>
                  <option value="LEASING_OF_PROPERTY">LEASING_OF_PROPERTY</option>
                  <option value="LOAN_ADVANCE_GIVEN">LOAN_ADVANCE_GIVEN</option>
                  <option value="LOAN_ADVANCE_TAKEN">LOAN_ADVANCE_TAKEN</option>
                  <option value="REMUNERATION_AND_PERQUISITES">REMUNERATION_AND_PERQUISITES</option>
                  <option value="BRAND_USAGE_OR_ROYALTY">BRAND_USAGE_OR_ROYALTY</option>
                </select>
              </div>
              <div className="flex gap-4">
                <label className="flex items-center gap-1 text-xs">
                  <input
                    type="checkbox"
                    checked={rptTxForm.inOrdinaryCourse}
                    onChange={(e) => setRptTxForm({ ...rptTxForm, inOrdinaryCourse: e.target.checked })}
                  />
                  In Ordinary Course
                </label>
                <label className="flex items-center gap-1 text-xs">
                  <input
                    type="checkbox"
                    checked={rptTxForm.isArmsLength}
                    onChange={(e) => setRptTxForm({ ...rptTxForm, isArmsLength: e.target.checked })}
                  />
                  At Arm's Length
                </label>
              </div>
              <div>
                <label className="text-xs text-secondary">Arm's Length Basis</label>
                <input
                  type="text"
                  className="input-field w-full text-sm"
                  placeholder="e.g. TP Benchmark Study, Third-Party Quotation"
                  value={rptTxForm.armLengthBasis}
                  onChange={(e) => setRptTxForm({ ...rptTxForm, armLengthBasis: e.target.value })}
                />
              </div>
              <div>
                <label className="text-xs text-secondary">Audit Committee Approval</label>
                <select
                  className="input-field w-full text-sm"
                  value={rptTxForm.auditCommitteeApproval}
                  onChange={(e) => setRptTxForm({ ...rptTxForm, auditCommitteeApproval: e.target.value })}
                >
                  <option value="OMNIBUS">OMNIBUS</option>
                  <option value="SPECIFIC">SPECIFIC</option>
                  <option value="EXEMPT">EXEMPT</option>
                  <option value="NONE">NONE</option>
                </select>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button type="button" className="btn btn-secondary text-sm" onClick={() => setShowRptTxModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary text-sm">
                  Record & Evaluate
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 3: RECORD LITIGATION CASE */}
      {showLitModal && (
        <div className="modal-backdrop">
          <div className="modal-content card max-w-lg">
            <h3 className="font-bold text-base mb-4">Record Litigation Case</h3>
            <form onSubmit={handleRecordLitigation} className="space-y-3">
              <div>
                <label className="text-xs text-secondary">Case Title</label>
                <input
                  type="text"
                  required
                  className="input-field w-full text-sm"
                  value={litForm.caseTitle}
                  onChange={(e) => setLitForm({ ...litForm, caseTitle: e.target.value })}
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-secondary">Case Number</label>
                  <input
                    type="text"
                    required
                    className="input-field w-full text-sm"
                    value={litForm.caseNumber}
                    onChange={(e) => setLitForm({ ...litForm, caseNumber: e.target.value })}
                  />
                </div>
                <div>
                  <label className="text-xs text-secondary">Court / Forum</label>
                  <input
                    type="text"
                    required
                    className="input-field w-full text-sm"
                    value={litForm.courtForum}
                    onChange={(e) => setLitForm({ ...litForm, courtForum: e.target.value })}
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-secondary">Category</label>
                  <select
                    className="input-field w-full text-sm"
                    value={litForm.category}
                    onChange={(e) => setLitForm({ ...litForm, category: e.target.value })}
                  >
                    <option value="CRIMINAL_PROCEEDING">CRIMINAL_PROCEEDING</option>
                    <option value="REGULATORY_STATUTORY_ACTION">REGULATORY_STATUTORY_ACTION</option>
                    <option value="EXCHANGE_DISCIPLINARY_ACTION">EXCHANGE_DISCIPLINARY_ACTION</option>
                    <option value="DIRECT_TAX">DIRECT_TAX</option>
                    <option value="INDIRECT_TAX">INDIRECT_TAX</option>
                    <option value="MATERIAL_CIVIL_LITIGATION">MATERIAL_CIVIL_LITIGATION</option>
                    <option value="INSOLVENCY_BANKRUPTCY">INSOLVENCY_BANKRUPTCY</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs text-secondary">Target Entity</label>
                  <input
                    type="text"
                    required
                    className="input-field w-full text-sm"
                    placeholder="Name of Director/Promoter/Issuer"
                    value={litForm.entityName}
                    onChange={(e) => setLitForm({ ...litForm, entityName: e.target.value })}
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-secondary">Claimed Amount (₹)</label>
                  <input
                    type="number"
                    step="0.01"
                    className="input-field w-full text-sm font-mono"
                    value={litForm.claimedAmount}
                    onChange={(e) => setLitForm({ ...litForm, claimedAmount: e.target.value })}
                  />
                </div>
                <div>
                  <label className="text-xs text-secondary">Risk Potential Impact</label>
                  <select
                    className="input-field w-full text-sm"
                    value={litForm.potentialImpact}
                    onChange={(e) => setLitForm({ ...litForm, potentialImpact: e.target.value })}
                  >
                    <option value="FINANCIAL">FINANCIAL</option>
                    <option value="OPERATIONAL">OPERATIONAL</option>
                    <option value="REPUTATIONAL">REPUTATIONAL</option>
                    <option value="MANAGEMENT_DISQUALIFICATION">MANAGEMENT_DISQUALIFICATION</option>
                    <option value="PERMIT_REVOCATION">PERMIT_REVOCATION</option>
                  </select>
                </div>
              </div>
              <div>
                <label className="text-xs text-secondary">Case Summary</label>
                <textarea
                  required
                  rows={2}
                  className="input-field w-full text-sm"
                  value={litForm.caseSummary}
                  onChange={(e) => setLitForm({ ...litForm, caseSummary: e.target.value })}
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button type="button" className="btn btn-secondary text-sm" onClick={() => setShowLitModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary text-sm">
                  Record & Screen
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 4: RECORD MATERIAL CONTRACT */}
      {showContractModal && (
        <div className="modal-backdrop">
          <div className="modal-content card max-w-lg">
            <h3 className="font-bold text-base mb-4">Record Material Contract</h3>
            <form onSubmit={handleRecordContract} className="space-y-3">
              <div>
                <label className="text-xs text-secondary">Contract Title</label>
                <input
                  type="text"
                  required
                  className="input-field w-full text-sm"
                  value={contractForm.title}
                  onChange={(e) => setContractForm({ ...contractForm, title: e.target.value })}
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-secondary">Reference Code</label>
                  <input
                    type="text"
                    required
                    className="input-field w-full text-sm font-mono"
                    value={contractForm.contractReference}
                    onChange={(e) => setContractForm({ ...contractForm, contractReference: e.target.value })}
                  />
                </div>
                <div>
                  <label className="text-xs text-secondary">Counterparty Name</label>
                  <input
                    type="text"
                    required
                    className="input-field w-full text-sm"
                    value={contractForm.counterpartyName}
                    onChange={(e) => setContractForm({ ...contractForm, counterpartyName: e.target.value })}
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-secondary">Revenue Concentration %</label>
                  <input
                    type="number"
                    step="0.1"
                    className="input-field w-full text-sm font-mono"
                    placeholder="e.g. 15.0"
                    value={contractForm.revenuePercentage}
                    onChange={(e) => setContractForm({ ...contractForm, revenuePercentage: e.target.value })}
                  />
                </div>
                <div>
                  <label className="text-xs text-secondary">Supply Concentration %</label>
                  <input
                    type="number"
                    step="0.1"
                    className="input-field w-full text-sm font-mono"
                    placeholder="e.g. 12.5"
                    value={contractForm.supplyPercentage}
                    onChange={(e) => setContractForm({ ...contractForm, supplyPercentage: e.target.value })}
                  />
                </div>
              </div>
              <div className="space-y-2 pt-1">
                <label className="flex items-center gap-2 text-xs font-semibold">
                  <input
                    type="checkbox"
                    checked={contractForm.hasChangeOfControl}
                    onChange={(e) => setContractForm({ ...contractForm, hasChangeOfControl: e.target.checked })}
                  />
                  Contains Change of Control (CoC) Covenant
                </label>
                {contractForm.hasChangeOfControl && (
                  <label className="flex items-center gap-2 text-xs pl-6">
                    <input
                      type="checkbox"
                      checked={contractForm.changeOfControlConsentObtained}
                      onChange={(e) => setContractForm({ ...contractForm, changeOfControlConsentObtained: e.target.checked })}
                    />
                    Counterparty Prior Written Consent Obtained
                  </label>
                )}
                <label className="flex items-center gap-2 text-xs">
                  <input
                    type="checkbox"
                    checked={contractForm.hasExclusivity}
                    onChange={(e) => setContractForm({ ...contractForm, hasExclusivity: e.target.checked })}
                  />
                  Has Exclusivity / Restrictive Covenants
                </label>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button type="button" className="btn btn-secondary text-sm" onClick={() => setShowContractModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary text-sm">
                  Record Contract
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
