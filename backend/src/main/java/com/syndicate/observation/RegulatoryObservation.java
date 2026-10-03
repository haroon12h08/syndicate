package com.syndicate.observation;

import com.syndicate.common.BaseEntity;
import com.syndicate.drhp.Disclosure;
import com.syndicate.drhp.DrhpDocument;
import com.syndicate.evidence.Evidence;
import com.syndicate.fact.Fact;
import com.syndicate.issue.IssueSeverity;
import com.syndicate.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A query from an exchange or regulator about a filing, and everything the answer rests on. */
@Entity
@Table(name = "regulatory_observations")
public class RegulatoryObservation extends BaseEntity {

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(nullable = false, updatable = false)
    private String authority;

    @Column(updatable = false)
    private String reference;

    @Column(name = "received_date", nullable = false, updatable = false)
    private LocalDate receivedDate;

    @Column(nullable = false)
    private String observation;

    @Column(name = "section_code")
    private String sectionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueSeverity severity = IssueSeverity.HIGH;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ObservationStatus status = ObservationStatus.OPEN;

    @Column(name = "response_deadline")
    private LocalDate responseDeadline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User owner;

    @Column
    private String response;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by_user_id")
    private User respondedBy;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    /** The compiled document the response was given against, so the state behind it is known. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drhp_document_id")
    private DrhpDocument document;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "observation_facts",
            joinColumns = @JoinColumn(name = "observation_id"),
            inverseJoinColumns = @JoinColumn(name = "fact_id"))
    private Set<Fact> relatedFacts = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "observation_evidence",
            joinColumns = @JoinColumn(name = "observation_id"),
            inverseJoinColumns = @JoinColumn(name = "evidence_id"))
    private Set<Evidence> relatedEvidence = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "observation_disclosures",
            joinColumns = @JoinColumn(name = "observation_id"),
            inverseJoinColumns = @JoinColumn(name = "disclosure_id"))
    private Set<Disclosure> relatedDisclosures = new HashSet<>();

    protected RegulatoryObservation() {
    }

    public RegulatoryObservation(UUID transactionId, String authority, String reference, LocalDate receivedDate,
                                 String observation, String sectionCode, IssueSeverity severity,
                                 LocalDate responseDeadline, User owner) {
        this.transactionId = transactionId;
        this.authority = authority;
        this.reference = reference;
        this.receivedDate = receivedDate;
        this.observation = observation;
        this.sectionCode = sectionCode;
        this.severity = severity == null ? IssueSeverity.HIGH : severity;
        this.responseDeadline = responseDeadline;
        this.owner = owner;
    }

    public void draftResponse(String response, User by) {
        this.response = response;
        this.respondedBy = by;
        this.respondedAt = Instant.now();
        this.status = ObservationStatus.RESPONSE_DRAFTED;
        this.approvedBy = null;
        this.approvedAt = null;
    }

    public void approveResponse(User by, DrhpDocument against) {
        this.approvedBy = by;
        this.approvedAt = Instant.now();
        this.document = against;
        this.status = ObservationStatus.RESPONSE_APPROVED;
    }

    public void markSent() {
        this.status = ObservationStatus.RESPONDED;
    }

    public void close() {
        this.status = ObservationStatus.CLOSED;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getAuthority() {
        return authority;
    }

    public String getReference() {
        return reference;
    }

    public LocalDate getReceivedDate() {
        return receivedDate;
    }

    public String getObservation() {
        return observation;
    }

    public String getSectionCode() {
        return sectionCode;
    }

    public IssueSeverity getSeverity() {
        return severity;
    }

    public ObservationStatus getStatus() {
        return status;
    }

    public LocalDate getResponseDeadline() {
        return responseDeadline;
    }

    public User getOwner() {
        return owner;
    }

    public String getResponse() {
        return response;
    }

    public User getRespondedBy() {
        return respondedBy;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public User getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public DrhpDocument getDocument() {
        return document;
    }

    public Set<Fact> getRelatedFacts() {
        return relatedFacts;
    }

    public Set<Evidence> getRelatedEvidence() {
        return relatedEvidence;
    }

    public Set<Disclosure> getRelatedDisclosures() {
        return relatedDisclosures;
    }
}
