package com.syndicate.rpt;

import com.syndicate.common.BaseEntity;
import com.syndicate.transaction.Transaction;
import com.syndicate.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "related_parties")
public class RelatedParty extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "party_type", nullable = false, length = 32)
    private String partyType; // INDIVIDUAL, BODY_CORPORATE, FIRM, HUF, TRUST

    @Column(length = 32)
    private String pan;

    @Column(name = "din_cin", length = 32)
    private String dinCin;

    @Column(name = "relationship_nature", nullable = false, length = 64)
    private String relationshipNature; // PROMOTER, DIRECTOR, KMP, etc.

    @Column(name = "relative_details", length = 255)
    private String relativeDetails;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_suggested", nullable = false)
    private boolean suggested = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_user_id")
    private User confirmedByUser;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected RelatedParty() {
    }

    public RelatedParty(Transaction transaction, String name, String partyType,
                        String pan, String dinCin, String relationshipNature,
                        String relativeDetails, LocalDate effectiveFrom,
                        LocalDate effectiveTo, boolean suggested) {
        this.transaction = transaction;
        this.name = name;
        this.partyType = partyType;
        this.pan = pan;
        this.dinCin = dinCin;
        this.relationshipNature = relationshipNature;
        this.relativeDetails = relativeDetails;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.suggested = suggested;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPartyType() {
        return partyType;
    }

    public void setPartyType(String partyType) {
        this.partyType = partyType;
    }

    public String getPan() {
        return pan;
    }

    public void setPan(String pan) {
        this.pan = pan;
    }

    public String getDinCin() {
        return dinCin;
    }

    public void setDinCin(String dinCin) {
        this.dinCin = dinCin;
    }

    public String getRelationshipNature() {
        return relationshipNature;
    }

    public void setRelationshipNature(String relationshipNature) {
        this.relationshipNature = relationshipNature;
    }

    public String getRelativeDetails() {
        return relativeDetails;
    }

    public void setRelativeDetails(String relativeDetails) {
        this.relativeDetails = relativeDetails;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public boolean isSuggested() {
        return suggested;
    }

    public void setSuggested(boolean suggested) {
        this.suggested = suggested;
    }

    public User getConfirmedByUser() {
        return confirmedByUser;
    }

    public void setConfirmedByUser(User confirmedByUser) {
        this.confirmedByUser = confirmedByUser;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }
}
