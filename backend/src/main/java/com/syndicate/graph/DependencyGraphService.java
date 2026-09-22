package com.syndicate.graph;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Traverses the {@code dependency_edges} view. Everything downstream of a change is found from
 * persisted relationships only (spec §6: "not hard-coded assumptions"), breadth-first, cycle-safe
 * and confined to one transaction.
 */
@Service
public class DependencyGraphService {

    private static final String DEPENDENTS_SQL = """
            SELECT * FROM dependency_edges
            WHERE transaction_id = ? AND to_type = ? AND to_lineage_id = ? AND from_is_current
            ORDER BY from_type, from_id
            """;

    private final JdbcTemplate jdbc;

    public DependencyGraphService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Current dependents that point directly at {@code node}. */
    public List<DependencyEdge> directDependents(UUID transactionId, GraphNode node) {
        return jdbc.query(DEPENDENTS_SQL, DependencyGraphService::mapEdge,
                transactionId, node.type().name(), node.lineageId());
    }

    /** Every current node that transitively depends on {@code changed}, nearest first. */
    public List<ImpactedNode> impactOf(UUID transactionId, GraphNode changed) {
        List<ImpactedNode> impacted = new ArrayList<>();
        Set<GraphNode> visited = new HashSet<>();
        visited.add(changed);
        Deque<GraphNode> frontier = new ArrayDeque<>();
        frontier.add(changed);
        int depth = 0;
        while (!frontier.isEmpty()) {
            depth++;
            int width = frontier.size();
            for (int i = 0; i < width; i++) {
                GraphNode node = frontier.poll();
                for (DependencyEdge edge : directDependents(transactionId, node)) {
                    if (visited.add(edge.from())) {
                        impacted.add(new ImpactedNode(edge.fromType(), edge.fromId(), edge.fromLineageId(), depth,
                                edge.kind(), edge.toType(), edge.toLineageId(), edge.pinnedVersionId(),
                                edge.isStale()));
                        frontier.add(edge.from());
                    }
                }
            }
        }
        return impacted;
    }

    private static DependencyEdge mapEdge(ResultSet rs, int row) throws SQLException {
        return new DependencyEdge(
                rs.getObject("transaction_id", UUID.class),
                GraphNodeType.valueOf(rs.getString("from_type")),
                rs.getObject("from_id", UUID.class),
                rs.getObject("from_lineage_id", UUID.class),
                GraphNodeType.valueOf(rs.getString("to_type")),
                rs.getObject("to_lineage_id", UUID.class),
                rs.getObject("pinned_version_id", UUID.class),
                rs.getBoolean("pinned_is_current"),
                rs.getString("kind"),
                rs.getBoolean("from_is_current"));
    }
}
