package com.syndicate.diligence;

import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TestApi.Actor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec §9: the questions a team already asks, held as transaction state. */
class DiligenceQuestionIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    TestApi api;
    Actor banker;
    Actor reviewer;
    UUID transactionId;
    UUID workstreamId;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        banker = api.register("Lead Bank " + UUID.randomUUID(), "MERCHANT_BANKER");
        Map tx = api.call(banker, HttpMethod.POST, "/api/transactions/start",
                Map.of("companyName", "Orion Tools Limited")).getBody();
        transactionId = UUID.fromString((String) tx.get("id"));
        reviewer = api.register("Reviewer " + UUID.randomUUID(), "OTHER_ADVISOR");
        api.call(banker, HttpMethod.POST, "/api/organizations/" + banker.organizationId() + "/members",
                Map.of("email", reviewer.email(), "role", "MEMBER"));
        api.create(banker, "/api/transactions/" + transactionId + "/memberships",
                Map.of("organizationId", banker.organizationId(), "email", reviewer.email(),
                        "role", "DUE_DILIGENCE_TEAM"));
        workstreamId = UUID.fromString((String) ((List<Map>) list("/api/transactions/" + transactionId
                + "/workstreams")).get(0).get("id"));
    }

    @SuppressWarnings("unchecked")
    private List<Map> list(String path) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(banker.token());
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), List.class).getBody();
    }

    private List<Map> questions() {
        return list("/api/transactions/" + transactionId + "/diligence-questions");
    }

    @Test
    void aNewTransactionArrivesWithTheStandardQuestions() {
        assertThat(questions()).hasSizeGreaterThan(10)
                .allSatisfy(q -> assertThat(q.get("status")).isEqualTo("OPEN"))
                .anySatisfy(q -> assertThat(String.valueOf(q.get("question"))).contains("site visit"));
    }

    @Test
    void anAnswerMustRestOnSomething() {
        UUID questionId = UUID.fromString((String) questions().get(0).get("id"));

        var bare = api.call(banker, HttpMethod.PUT, "/api/diligence-questions/" + questionId + "/answer",
                Map.of("answer", "We looked into it and it is fine."));
        assertThat(bare.getStatusCode().value()).isEqualTo(400);
        assertThat(String.valueOf(bare.getBody().get("message"))).contains("rests on");

        UUID evidenceId = api.uploadEvidence(banker, workstreamId, "board-minutes.txt",
                ("minutes " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "OTHER");
        var answered = api.call(banker, HttpMethod.PUT, "/api/diligence-questions/" + questionId + "/answer",
                Map.of("answer", "Two allotments in the lookback period, both to unrelated investors.",
                        "evidenceIds", List.of(evidenceId)));
        assertThat(answered.getBody().get("status")).isEqualTo("ANSWERED");
        assertThat((List<?>) answered.getBody().get("supportingEvidenceIds")).hasSize(1);
    }

    @Test
    void theAnswererCannotAcceptTheirOwnAnswer() {
        UUID questionId = UUID.fromString((String) questions().get(0).get("id"));
        UUID evidenceId = api.uploadEvidence(banker, workstreamId, "note.txt",
                ("note " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "OTHER");
        api.call(banker, HttpMethod.PUT, "/api/diligence-questions/" + questionId + "/answer",
                Map.of("answer", "Answered.", "evidenceIds", List.of(evidenceId)));

        assertThat(api.call(banker, HttpMethod.POST, "/api/diligence-questions/" + questionId + "/accept", null)
                .getBody().get("code")).isEqualTo("ANSWER_NOT_INDEPENDENT");
        assertThat(api.call(reviewer, HttpMethod.POST, "/api/diligence-questions/" + questionId + "/accept", null)
                .getBody().get("status")).isEqualTo("ACCEPTED");
    }

    @Test
    void aQuestionCanBeRuledOutWithAReason() {
        UUID questionId = UUID.fromString((String) questions().get(1).get("id"));
        var result = api.call(banker, HttpMethod.PUT, "/api/diligence-questions/" + questionId + "/answer",
                Map.of("notApplicableReason", "The company has never raised equity before this issue."));
        assertThat(result.getBody().get("status")).isEqualTo("NOT_APPLICABLE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void openQuestionsHoldTheTransactionBack() {
        Map workbench = api.call(banker, HttpMethod.GET, "/api/transactions/" + transactionId + "/workbench", null)
                .getBody();
        assertThat((List<Map>) workbench.get("blockers"))
                .anySatisfy(b -> assertThat(b.get("type")).isEqualTo("DILIGENCE_QUESTION_OPEN"));
        assertThat(workbench.get("readiness")).isEqualTo("NOT_READY");
    }
}
