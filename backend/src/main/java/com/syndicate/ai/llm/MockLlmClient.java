package com.syndicate.ai.llm;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Component
public class MockLlmClient implements LlmClient {

    private final AtomicReference<Function<LlmPrompt, LlmResponse>> customHandler = new AtomicReference<>();
    private final AtomicReference<RuntimeException> failureToThrow = new AtomicReference<>();

    @Override
    public LlmResponse generate(LlmPrompt prompt) {
        RuntimeException failure = failureToThrow.getAndSet(null);
        if (failure != null) {
            throw failure;
        }

        Function<LlmPrompt, LlmResponse> handler = customHandler.get();
        if (handler != null) {
            return handler.apply(prompt);
        }

        long start = System.currentTimeMillis();
        String rawOutput;
        double confidence = 0.95;

        if ("document-classification".equals(prompt.templateName())) {
            String userPromptLower = prompt.userPrompt() != null ? prompt.userPrompt().toLowerCase() : "";
            String docType = "AUDITED_FINANCIAL_STATEMENT";
            String reasoning = "Detected balance sheet and profit & loss line items.";

            if (userPromptLower.contains("resolution") || userPromptLower.contains("board meeting")) {
                docType = "BOARD_RESOLUTION";
                reasoning = "Detected board meeting minutes and resolution clauses.";
            } else if (userPromptLower.contains("licence") || userPromptLower.contains("license")) {
                docType = "LICENCE";
                reasoning = "Detected regulatory operating license text.";
            } else if (userPromptLower.contains("contract") || userPromptLower.contains("agreement")) {
                docType = "CONTRACT";
                reasoning = "Detected commercial contract obligations.";
            } else if (userPromptLower.contains("certificate")) {
                docType = "CERTIFICATE";
                reasoning = "Detected statutory certificate.";
            }

            rawOutput = """
                    {
                      "documentType": "%s",
                      "confidence": %.2f,
                      "reasoning": "%s"
                    }
                    """.formatted(docType, confidence, reasoning);

        } else if ("candidate-fact-extraction".equals(prompt.templateName())) {
            rawOutput = """
                    {
                      "candidates": [
                        {
                          "label": "EBITDA",
                          "value": "42.18 Cr",
                          "period": "FY2026",
                          "pageNumber": 1,
                          "confidence": 0.96,
                          "bboxX": 120.0,
                          "bboxY": 280.0,
                          "bboxWidth": 75.0,
                          "bboxHeight": 18.0
                        },
                        {
                          "label": "Net Profit",
                          "value": "18.50 Cr",
                          "period": "FY2026",
                          "pageNumber": 1,
                          "confidence": 0.92,
                          "bboxX": 120.0,
                          "bboxY": 310.0,
                          "bboxWidth": 75.0,
                          "bboxHeight": 18.0
                        },
                        {
                          "label": "Total Revenue",
                          "value": "150.00 Cr",
                          "period": "FY2026",
                          "pageNumber": 1,
                          "confidence": 0.98,
                          "bboxX": 120.0,
                          "bboxY": 240.0,
                          "bboxWidth": 80.0,
                          "bboxHeight": 18.0
                        }
                      ]
                    }
                    """;
        } else if ("suggestion-generation".equals(prompt.templateName())) {
            rawOutput = """
                    {
                      "summary": "Completed diligence cross-checks across candidates, evidence inventory, and checklist requirements.",
                      "riskLevel": "MEDIUM",
                      "recommendations": [
                        "Review detected pre-conflicts before candidate acceptance.",
                        "Upload mandatory BSE SME audited financial statements and board resolutions."
                      ]
                    }
                    """;
        } else if ("disclosure-drafting".equals(prompt.templateName())) {
            String title = "Capital Structure and Financial Summary";
            if (prompt.userPrompt() != null && prompt.userPrompt().contains("FINANCIAL_INFORMATION")) {
                title = "Summary of Financial Information";
            }
            rawOutput = """
                    {
                      "suggestedTitle": "%s",
                      "bodyTemplate": "For the financial year ended March 31, 2026, the Issuer achieved total revenue of {{fact:issuer.revenue@FY2026}} with an EBITDA of {{fact:issuer.ebitda@FY2026}} and a net profit of {{fact:issuer.pat@FY2026}}. As of the date of this Draft Red Herring Prospectus, the authorized share capital of our Company is {{fact:issuer.authorized_capital@FY2026}} and the paid-up equity share capital is {{fact:issuer.paid_up_capital@FY2026}}.",
                      "citedFactKeys": [
                        "issuer.revenue",
                        "issuer.ebitda",
                        "issuer.pat",
                        "issuer.authorized_capital",
                        "issuer.paid_up_capital"
                      ]
                    }
                    """.formatted(title);
        } else if ("observation-response".equals(prompt.templateName())) {
            rawOutput = """
                    {
                      "suggestedResponse": "With reference to the observation raised by the regulator regarding operational results and inventory movements, the Issuer respectfully submits that inventory levels expanded to support scheduled deliveries under executed commercial agreements. As substantiated by the Audited Financial Statements, total revenue reached {{fact:issuer.revenue@FY2026}} reflecting genuine trading expansion. All physical inventory has been reconciled with statutory stock registries.",
                      "suggestedFactIds": [],
                      "suggestedEvidenceIds": [],
                      "suggestedDisclosureIds": [],
                      "reasoning": "Directly grounds the management explanation on audited financial statements and commercial contract delivery schedules."
                    }
                    """;
        } else {
            rawOutput = "{}";
        }

        long duration = Math.max(1, System.currentTimeMillis() - start);
        int promptTokens = prompt.userPrompt() != null ? Math.max(10, prompt.userPrompt().length() / 4) : 50;
        int completionTokens = rawOutput.length() / 4;
        BigDecimal cost = BigDecimal.valueOf(0.000003).multiply(BigDecimal.valueOf(promptTokens))
                .add(BigDecimal.valueOf(0.000015).multiply(BigDecimal.valueOf(completionTokens)));

        return new LlmResponse(rawOutput, promptTokens, completionTokens, cost, duration,
                getProvider(), getModel(), getModelVersion());
    }

    @Override
    public String getProvider() {
        return "mock";
    }

    @Override
    public String getModel() {
        return "mock-intelligence-v1";
    }

    @Override
    public String getModelVersion() {
        return "2026-10";
    }

    public void setCustomHandler(Function<LlmPrompt, LlmResponse> handler) {
        this.customHandler.set(handler);
    }

    public void clearCustomHandler() {
        this.customHandler.set(null);
    }

    public void failNextCall(RuntimeException ex) {
        this.failureToThrow.set(ex);
    }
}
