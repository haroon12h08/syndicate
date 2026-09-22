package com.syndicate.graph;

import java.util.UUID;

/** One row of the {@code dependency_edges} view: {@code from} depends on {@code to}. */
public record DependencyEdge(UUID transactionId, GraphNodeType fromType, UUID fromId, UUID fromLineageId,
                             GraphNodeType toType, UUID toLineageId, UUID pinnedVersionId,
                             boolean pinnedIsCurrent, String kind, boolean fromIsCurrent) {

    public GraphNode from() {
        return new GraphNode(fromType, fromLineageId);
    }

    public boolean isStale() {
        return !pinnedIsCurrent;
    }
}
