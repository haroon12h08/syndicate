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
