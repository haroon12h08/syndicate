package com.syndicate.graph;

import java.util.UUID;

/** A node in the dependency graph, identified by lineage so it survives new versions. */
public record GraphNode(GraphNodeType type, UUID lineageId) {
}
