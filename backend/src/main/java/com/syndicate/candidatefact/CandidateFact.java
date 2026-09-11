package com.syndicate.candidatefact;

import com.syndicate.common.BaseEntity;
import com.syndicate.evidence.Evidence;
import com.syndicate.fact.Fact;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "candidate_facts")
public class CandidateFact extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workstream_id", nullable = false)
    private Workstream workstream;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_id", nullable = false)
    private Evidence evidence;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String value;

    @Column
    private String period;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "bbox_x", nullable = false)
    private double bboxX;

    @Column(name = "bbox_y", nullable = false)
    private double bboxY;

    @Column(name = "bbox_width", nullable = false)
    private double bboxWidth;

    @Column(name = "bbox_height", nullable = false)
    private double bboxHeight;

    @Column(name = "page_image_width", nullable = false)
    private int pageImageWidth;

    @Column(name = "page_image_height", nullable = false)
    private int pageImageHeight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CandidateFactSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CandidateFactStatus status;

    @Column(name = "review_note")
    private String reviewNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedByUser;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resulting_fact_id")
    private Fact resultingFact;

    protected CandidateFact() {
    }

    public CandidateFact(Workstream workstream, Evidence evidence, String label, String value, String period,
                          int pageNumber, double bboxX, double bboxY, double bboxWidth, double bboxHeight,
                          int pageImageWidth, int pageImageHeight, CandidateFactSource source) {
        this.workstream = workstream;
        this.evidence = evidence;
        this.label = label;
        this.value = value;
        this.period = period;
        this.pageNumber = pageNumber;
        this.bboxX = bboxX;
        this.bboxY = bboxY;
        this.bboxWidth = bboxWidth;
        this.bboxHeight = bboxHeight;
        this.pageImageWidth = pageImageWidth;
        this.pageImageHeight = pageImageHeight;
        this.source = source;
        this.status = CandidateFactStatus.PENDING;
    }

    public Workstream getWorkstream() {
        return workstream;
    }

    public Evidence getEvidence() {
        return evidence;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public String getPeriod() {
        return period;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public double getBboxX() {
        return bboxX;
    }

    public double getBboxY() {
        return bboxY;
    }

    public double getBboxWidth() {
        return bboxWidth;
    }

    public double getBboxHeight() {
        return bboxHeight;
    }

    public int getPageImageWidth() {
        return pageImageWidth;
    }

    public int getPageImageHeight() {
        return pageImageHeight;
    }

    public CandidateFactSource getSource() {
        return source;
    }

    public CandidateFactStatus getStatus() {
        return status;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public User getReviewedByUser() {
        return reviewedByUser;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Fact getResultingFact() {
        return resultingFact;
    }

    public void accept(User reviewer, String note, Fact resultingFact) {
        this.status = CandidateFactStatus.ACCEPTED;
        this.reviewedByUser = reviewer;
        this.reviewedAt = Instant.now();
        this.reviewNote = note;
        this.resultingFact = resultingFact;
    }

    public void reject(User reviewer, String note) {
        this.status = CandidateFactStatus.REJECTED;
        this.reviewedByUser = reviewer;
        this.reviewedAt = Instant.now();
        this.reviewNote = note;
    }
}
