package com.syndicate.support;

import com.syndicate.support.TestApi.Actor;

import java.util.Map;
import java.util.UUID;

/** A lead organization with a company, an SME IPO transaction and one workstream. */
public record TransactionFixture(Actor lead, UUID companyId, UUID transactionId, UUID workstreamId) {

    public static TransactionFixture create(TestApi api, String workstreamType) {
        Actor lead = api.register("Lead Bank " + UUID.randomUUID(), "MERCHANT_BANKER");
        UUID companyId = api.create(lead, "/api/companies",
                Map.of("legalName", "Issuer Pvt Ltd", "ownerOrganizationId", lead.organizationId()));
        UUID transactionId = api.create(lead, "/api/companies/" + companyId + "/transactions",
                Map.of("name", "SME IPO", "type", "SME_IPO", "leadOrganizationId", lead.organizationId(),
                        "creatorRole", "LEAD_BANKER"));
        UUID workstreamId = api.create(lead, "/api/transactions/" + transactionId + "/workstreams",
                Map.of("type", workstreamType));
        return new TransactionFixture(lead, companyId, transactionId, workstreamId);
    }

    /**
     * Answers every blocking diligence question, as a team would before compiling a filing copy.
     * Each answer points at the given document so it rests on something.
     */
    @SuppressWarnings("unchecked")
    public void settleBlockingQuestions(TestApi api, java.util.UUID evidenceId) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(lead.token());
        java.util.List<java.util.Map<String, Object>> questions = api.rest()
                .exchange("/api/transactions/" + transactionId + "/diligence-questions",
                        org.springframework.http.HttpMethod.GET,
                        new org.springframework.http.HttpEntity<>(headers), java.util.List.class)
                .getBody();
        for (java.util.Map<String, Object> question : questions) {
            if (!"OPEN".equals(question.get("status")) || !"BLOCKING".equals(question.get("severity"))) {
                continue;
            }
            api.call(lead, org.springframework.http.HttpMethod.PUT,
                    "/api/diligence-questions/" + question.get("id") + "/answer",
                    Map.of("answer", "Reviewed with the issuer; documented in the linked evidence.",
                            "evidenceIds", java.util.List.of(evidenceId)));
        }
    }

    /** Adds a new user to the lead organization and seats them on the transaction with the given role. */
    public Actor addMember(TestApi api, String role) {
        Actor member = api.register("Own Org " + UUID.randomUUID(), "OTHER_ADVISOR");
        api.call(lead, org.springframework.http.HttpMethod.POST, "/api/organizations/" + lead.organizationId() + "/members",
                Map.of("email", member.email(), "role", "MEMBER"));
        api.create(lead, "/api/transactions/" + transactionId + "/memberships",
                Map.of("organizationId", lead.organizationId(), "email", member.email(), "role", role));
        return member;
    }
}
