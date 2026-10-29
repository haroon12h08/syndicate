package com.syndicate.search;

import com.syndicate.search.dto.SearchResultDto;
import com.syndicate.search.dto.TransactionSearchResultsDto;
import com.syndicate.transaction.TransactionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TransactionSearchService {

    private final JdbcTemplate jdbc;
    private final TransactionService transactionService;

    public TransactionSearchService(JdbcTemplate jdbc, TransactionService transactionService) {
        this.jdbc = jdbc;
        this.transactionService = transactionService;
    }

    public TransactionSearchResultsDto search(UUID transactionId, String query, String typesFilter, int limit, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);

        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.isEmpty()) {
            return new TransactionSearchResultsDto(query, 0, Map.of(), List.of());
        }

        Set<String> allowedTypes = parseTypesFilter(typesFilter);
        int maxResults = Math.min(Math.max(limit, 1), 100);

        List<SearchResultDto> allResults = new ArrayList<>();
        String pattern = "%" + trimmedQuery.toLowerCase() + "%";

        // 1. FACTS
        if (allowedTypes.isEmpty() || allowedTypes.contains("FACT")) {
            String sql = """
                SELECT f.id, f.workstream_id, f.label, f.fact_key, f.value, f.period, f.unit,
                       f.status, f.materiality, f.created_at, u.full_name AS creator_name,
                       u_v.full_name AS verifier_name
                FROM facts f
                JOIN workstreams w ON f.workstream_id = w.id
                LEFT JOIN users u ON f.created_by_user_id = u.id
                LEFT JOIN users u_v ON f.verified_by_user_id = u_v.id
                WHERE w.transaction_id = ?
                  AND (lower(f.label) LIKE ? OR lower(f.fact_key) LIKE ? OR lower(f.value) LIKE ?
                       OR lower(coalesce(f.period, '')) LIKE ? OR lower(coalesce(f.unit, '')) LIKE ?)
                ORDER BY f.created_at DESC
                LIMIT ?
                """;
            allResults.addAll(jdbc.query(sql, (rs, rowNum) -> mapFact(rs, transactionId, trimmedQuery),
                    transactionId, pattern, pattern, pattern, pattern, pattern, maxResults));
        }

        // 2. EVIDENCE
        if (allowedTypes.isEmpty() || allowedTypes.contains("EVIDENCE")) {
            String sql = """
                SELECT e.id, e.workstream_id, e.file_name, e.document_type, e.processing_status,
                       e.extracted_text, e.uploaded_at, e.created_at, u.full_name AS uploader_name
                FROM evidence e
                JOIN workstreams w ON e.workstream_id = w.id
                LEFT JOIN users u ON e.uploaded_by_user_id = u.id
                WHERE w.transaction_id = ?
                  AND (lower(e.file_name) LIKE ? OR lower(coalesce(e.extracted_text, '')) LIKE ?
                       OR lower(e.document_type) LIKE ? OR lower(coalesce(e.suggested_document_type, '')) LIKE ?)
                ORDER BY e.created_at DESC
                LIMIT ?
                """;
            allResults.addAll(jdbc.query(sql, (rs, rowNum) -> mapEvidence(rs, transactionId, trimmedQuery),
                    transactionId, pattern, pattern, pattern, pattern, maxResults));
        }

        // 3. CANDIDATE_FACT
        if (allowedTypes.isEmpty() || allowedTypes.contains("CANDIDATE_FACT")) {
            String sql = """
                SELECT cf.id, cf.workstream_id, cf.label, cf.value, cf.period, cf.status,
                       cf.source, cf.confidence, cf.created_at, e.file_name
                FROM candidate_facts cf
                JOIN workstreams w ON cf.workstream_id = w.id
                LEFT JOIN evidence e ON cf.evidence_id = e.id
                WHERE w.transaction_id = ?
                  AND (lower(cf.label) LIKE ? OR lower(cf.value) LIKE ?
                       OR lower(coalesce(cf.period, '')) LIKE ? OR lower(coalesce(cf.review_note, '')) LIKE ?)
                ORDER BY cf.created_at DESC
                LIMIT ?
                """;
            allResults.addAll(jdbc.query(sql, (rs, rowNum) -> mapCandidateFact(rs, transactionId, trimmedQuery),
                    transactionId, pattern, pattern, pattern, pattern, maxResults));
        }

        // 4. DISCLOSURE
        if (allowedTypes.isEmpty() || allowedTypes.contains("DISCLOSURE")) {
            String sql = """
                SELECT d.id, d.section_code, d.title, d.body_template, d.status, d.created_at
                FROM disclosures d
                WHERE d.transaction_id = ?
                  AND (lower(d.title) LIKE ? OR lower(d.section_code) LIKE ?
                       OR lower(coalesce(d.body_template, '')) LIKE ?)
                ORDER BY d.created_at DESC
                LIMIT ?
                """;
            allResults.addAll(jdbc.query(sql, (rs, rowNum) -> mapDisclosure(rs, transactionId, trimmedQuery),
                    transactionId, pattern, pattern, pattern, maxResults));
        }

        // 5. DILIGENCE_QUESTION
        if (allowedTypes.isEmpty() || allowedTypes.contains("DILIGENCE_QUESTION")) {
            String sql = """
                SELECT dq.id, dq.code, dq.question, dq.workstream_type, dq.severity, dq.status,
                       dq.answer, dq.created_at
                FROM diligence_questions dq
                WHERE dq.transaction_id = ?
                  AND (lower(dq.question) LIKE ? OR lower(coalesce(dq.code, '')) LIKE ?
                       OR lower(dq.workstream_type) LIKE ? OR lower(coalesce(dq.answer, '')) LIKE ?)
                ORDER BY dq.created_at DESC
                LIMIT ?
                """;
            allResults.addAll(jdbc.query(sql, (rs, rowNum) -> mapDiligenceQuestion(rs, transactionId, trimmedQuery),
                    transactionId, pattern, pattern, pattern, pattern, maxResults));
        }

        // 6. CONFLICT
        if (allowedTypes.isEmpty() || allowedTypes.contains("CONFLICT")) {
            String sql = """
                SELECT fc.id, fc.conflict_key, fc.fact_key, fc.period, fc.status, fc.detected_at, fc.created_at
                FROM fact_conflicts fc
                WHERE fc.transaction_id = ?
                  AND (lower(fc.conflict_key) LIKE ? OR lower(fc.fact_key) LIKE ?
                       OR lower(coalesce(fc.period, '')) LIKE ?)
                ORDER BY fc.created_at DESC
                LIMIT ?
                """;
            allResults.addAll(jdbc.query(sql, (rs, rowNum) -> mapConflict(rs, transactionId, trimmedQuery),
                    transactionId, pattern, pattern, pattern, maxResults));
        }

        // 7. AUDIT_EVENT
        if (allowedTypes.isEmpty() || allowedTypes.contains("AUDIT_EVENT")) {
            String sql = """
                SELECT ae.id, ae.action, ae.entity_type, ae.summary, ae.reason, ae.occurred_at,
                       ae.created_at, u.full_name AS actor_name
                FROM audit_events ae
                LEFT JOIN users u ON ae.actor_user_id = u.id
                WHERE ae.transaction_id = ?
                  AND (lower(ae.action) LIKE ? OR lower(ae.entity_type) LIKE ?
                       OR lower(ae.summary) LIKE ? OR lower(coalesce(ae.reason, '')) LIKE ?)
                ORDER BY ae.occurred_at DESC
                LIMIT ?
                """;
            allResults.addAll(jdbc.query(sql, (rs, rowNum) -> mapAuditEvent(rs, transactionId, trimmedQuery),
                    transactionId, pattern, pattern, pattern, pattern, maxResults));
        }

        // Tally counts by type
        Map<String, Integer> countsByType = allResults.stream()
                .collect(Collectors.groupingBy(SearchResultDto::entityType, Collectors.summingInt(e -> 1)));

        // Sort by score descending, then created_at descending
        List<SearchResultDto> sortedResults = allResults.stream()
                .sorted(Comparator.comparing(SearchResultDto::score).reversed()
                        .thenComparing(SearchResultDto::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(maxResults)
                .toList();

        return new TransactionSearchResultsDto(trimmedQuery, allResults.size(), countsByType, sortedResults);
    }

    private Set<String> parseTypesFilter(String typesFilter) {
        if (typesFilter == null || typesFilter.isBlank()) return Set.of();
        return Arrays.stream(typesFilter.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private SearchResultDto mapFact(ResultSet rs, UUID txId, String query) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        UUID wsId = (UUID) rs.getObject("workstream_id");
        String label = rs.getString("label");
        String factKey = rs.getString("fact_key");
        String value = rs.getString("value");
        String period = rs.getString("period");
        String unit = rs.getString("unit");
        String status = rs.getString("status");
        String materiality = rs.getString("materiality");
        String creator = rs.getString("creator_name");
        String verifier = rs.getString("verifier_name");
        Timestamp createdAt = rs.getTimestamp("created_at");

        String displayVal = value + (unit != null ? " " + unit : "");
        String subtitle = factKey + (period != null ? " (" + period + ")" : "") + " = " + displayVal;
        String provenance = (verifier != null ? "Verified by " + verifier : "Created by " + (creator != null ? creator : "System"))
                + (materiality != null ? " · " + materiality : "");

        double score = 1.0;
        if (label != null && label.equalsIgnoreCase(query)) score = 2.0;

        return new SearchResultDto(
                "FACT", id, wsId, label, subtitle, displayVal, status, provenance,
                "/facts/" + id + "/trace", score, createdAt != null ? createdAt.toInstant() : Instant.now()
        );
    }

    private SearchResultDto mapEvidence(ResultSet rs, UUID txId, String query) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        UUID wsId = (UUID) rs.getObject("workstream_id");
        String fileName = rs.getString("file_name");
        String docType = rs.getString("document_type");
        String status = rs.getString("processing_status");
        String text = rs.getString("extracted_text");
        String uploader = rs.getString("uploader_name");
        Timestamp createdAt = rs.getTimestamp("created_at");

        String snippet = extractSnippet(text, query);
        String subtitle = docType + (snippet != null ? " · Snippet: \"" + snippet + "\"" : "");
        String provenance = "Uploaded by " + (uploader != null ? uploader : "User");

        double score = 0.9;
        if (fileName != null && fileName.toLowerCase().contains(query.toLowerCase())) score = 1.5;

        return new SearchResultDto(
                "EVIDENCE", id, wsId, fileName, subtitle, snippet, status, provenance,
                "/transactions/" + txId + "?tab=documents&evidenceId=" + id,
                score, createdAt != null ? createdAt.toInstant() : Instant.now()
        );
    }

    private SearchResultDto mapCandidateFact(ResultSet rs, UUID txId, String query) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        UUID wsId = (UUID) rs.getObject("workstream_id");
        String label = rs.getString("label");
        String value = rs.getString("value");
        String period = rs.getString("period");
        String status = rs.getString("status");
        String source = rs.getString("source");
        Double confidence = rs.getDouble("confidence");
        String fileName = rs.getString("file_name");
        Timestamp createdAt = rs.getTimestamp("created_at");

        String subtitle = (period != null ? "[" + period + "] " : "") + value;
        String provenance = "Extracted from " + (fileName != null ? fileName : "document")
                + " via " + (source != null ? source : "OCR")
                + (confidence != null && confidence > 0 ? String.format(" (%.0f%% conf)", confidence * 100) : "");

        return new SearchResultDto(
                "CANDIDATE_FACT", id, wsId, label, subtitle, value, status, provenance,
                "/workstreams/" + wsId, 0.85, createdAt != null ? createdAt.toInstant() : Instant.now()
        );
    }

    private SearchResultDto mapDisclosure(ResultSet rs, UUID txId, String query) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        String section = rs.getString("section_code");
        String title = rs.getString("title");
        String body = rs.getString("body_template");
        String status = rs.getString("status");
        Timestamp createdAt = rs.getTimestamp("created_at");

        String snippet = extractSnippet(body, query);
        String subtitle = "Section " + section + (snippet != null ? " · \"" + snippet + "\"" : "");

        return new SearchResultDto(
                "DISCLOSURE", id, null, title, subtitle, snippet, status, "DRHP Section " + section,
                "/transactions/" + txId + "?tab=drhp&section=" + section, 0.95,
                createdAt != null ? createdAt.toInstant() : Instant.now()
        );
    }

    private SearchResultDto mapDiligenceQuestion(ResultSet rs, UUID txId, String query) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        String code = rs.getString("code");
        String question = rs.getString("question");
        String category = rs.getString("workstream_type");
        String severity = rs.getString("severity");
        String status = rs.getString("status");
        String answer = rs.getString("answer");
        Timestamp createdAt = rs.getTimestamp("created_at");

        String snippet = extractSnippet(answer, query);
        String subtitle = (code != null ? code + " · " : "") + category + (snippet != null ? " · Answer: \"" + snippet + "\"" : "");
        String provenance = "Severity: " + severity;

        return new SearchResultDto(
                "DILIGENCE_QUESTION", id, null, question, subtitle, snippet, status, provenance,
                "/transactions/" + txId + "?tab=diligence&questionId=" + id, 0.9,
                createdAt != null ? createdAt.toInstant() : Instant.now()
        );
    }

    private SearchResultDto mapConflict(ResultSet rs, UUID txId, String query) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        String conflictKey = rs.getString("conflict_key");
        String factKey = rs.getString("fact_key");
        String period = rs.getString("period");
        String status = rs.getString("status");
        Timestamp detectedAt = rs.getTimestamp("detected_at");
        Timestamp createdAt = rs.getTimestamp("created_at");

        String title = "Conflict: " + factKey + (period != null ? " (" + period + ")" : "");
        String subtitle = conflictKey;
        String provenance = "Detected " + (detectedAt != null ? detectedAt.toInstant() : "recently");

        return new SearchResultDto(
                "CONFLICT", id, null, title, subtitle, null, status, provenance,
                "/transactions/" + txId + "?tab=conflicts&conflictId=" + id, 1.1,
                createdAt != null ? createdAt.toInstant() : Instant.now()
        );
    }

    private SearchResultDto mapAuditEvent(ResultSet rs, UUID txId, String query) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        String action = rs.getString("action");
        String entityType = rs.getString("entity_type");
        String summary = rs.getString("summary");
        String actor = rs.getString("actor_name");
        Timestamp occurredAt = rs.getTimestamp("occurred_at");
        Timestamp createdAt = rs.getTimestamp("created_at");

        String title = action + " (" + entityType + ")";
        String provenance = "By " + (actor != null ? actor : "System") + " at " + (occurredAt != null ? occurredAt.toInstant() : "");

        return new SearchResultDto(
                "AUDIT_EVENT", id, null, title, summary, summary, action, provenance,
                "/transactions/" + txId + "?tab=audit&eventId=" + id, 0.7,
                createdAt != null ? createdAt.toInstant() : Instant.now()
        );
    }

    private String extractSnippet(String content, String query) {
        if (content == null || content.isBlank() || query == null || query.isBlank()) return null;
        int idx = content.toLowerCase().indexOf(query.toLowerCase());
        if (idx < 0) return null;
        int start = Math.max(0, idx - 30);
        int end = Math.min(content.length(), idx + query.length() + 30);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < content.length() ? "..." : "";
        return prefix + content.substring(start, end).replaceAll("\\s+", " ").trim() + suffix;
    }
}
