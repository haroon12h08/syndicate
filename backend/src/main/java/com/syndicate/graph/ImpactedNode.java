package com.syndicate.graph;

import java.util.UUID;

/**
 * A dependent reached from a changed node. {@code depth} 1 is a direct dependent; {@code stale}
 * means the edge that reached it pins a version of its dependency that is no longer current.
 */
public record ImpactedNode(GraphNodeType type, UUID id, UUID lineageId, int depth, String viaKind,
                           GraphNodeType dependsOnType, UUID dependsOnLineageId, UUID pinnedVersionId,
                           boolean stale) {
}
