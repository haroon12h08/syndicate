package com.syndicate.security;

import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TestApi.Actor;
import com.syndicate.support.TransactionFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec §30: transaction-critical actions are allowed only for explicitly permitted roles.
 * Each test pins the expected allow-set so a permission change is a deliberate, reviewed diff.
 */
class RolePermissionIT extends IntegrationTestBase {

    enum Role {
        ISSUER_ADMIN, PROMOTER, CFO, COMPANY_SECRETARY, LEAD_BANKER, DUE_DILIGENCE_TEAM,
        LEAD_LAWYER, LEGAL_ASSOCIATE, AUDITOR, TAX_ADVISOR, REGULATORY_CONSULTANT, ADVISOR
    }

    @Autowired TestRestTemplate rest;

    private int status(org.springframework.http.ResponseEntity<?> res) {
        return res.getStatusCode().value();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void verifyingFinancialFactsIsRestricted(Role role) {
        TestApi api = new TestApi(rest);
        TransactionFixture tx = TransactionFixture.create(api, "FINANCIAL_DUE_DILIGENCE");
        Actor member = tx.addMember(api, role.name());
        UUID factId = api.create(tx.lead(), "/api/workstreams/" + tx.workstreamId() + "/facts",
                Map.of("label", "Revenue", "value", "42.18", "period", "FY2026"));

        int code = status(api.call(member, HttpMethod.POST, "/api/facts/" + factId + "/verify", null));

        boolean allowed = Set.of(Role.ISSUER_ADMIN, Role.AUDITOR).contains(role);
        assertThat(code).as(role + " verify financial fact").isEqualTo(allowed ? 200 : 403);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void managingMembershipIsRestricted(Role role) {
        TestApi api = new TestApi(rest);
        TransactionFixture tx = TransactionFixture.create(api, "LEGAL_DUE_DILIGENCE");
        Actor member = tx.addMember(api, role.name());
        Actor newcomer = api.register("Newcomer " + UUID.randomUUID(), "OTHER_ADVISOR");
        api.call(tx.lead(), HttpMethod.POST, "/api/organizations/" + tx.lead().organizationId() + "/members",
                Map.of("email", newcomer.email(), "role", "MEMBER"));

        int code = status(api.call(member, HttpMethod.POST, "/api/transactions/" + tx.transactionId() + "/memberships",
                Map.of("organizationId", tx.lead().organizationId(), "email", newcomer.email(), "role", "ADVISOR")));

        boolean allowed = Set.of(Role.ISSUER_ADMIN, Role.LEAD_BANKER).contains(role);
        assertThat(code).as(role + " add member").isEqualTo(allowed ? 201 : 403);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void deletingATransactionIsIssuerAdminOnly(Role role) {
        TestApi api = new TestApi(rest);
        TransactionFixture tx = TransactionFixture.create(api, "LEGAL_DUE_DILIGENCE");
        Actor member = tx.addMember(api, role.name());

        int code = status(api.raw(member, HttpMethod.DELETE, "/api/transactions/" + tx.transactionId()));

        assertThat(code).as(role + " delete transaction").isEqualTo(role == Role.ISSUER_ADMIN ? 204 : 403);
    }
}
