package com.syndicate.provenance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Deterministic SHA-256 Merkle tree over the inputs a filing was compiled from.
 *
 * <p>The point is reproducibility: given the same facts, evidence hashes and signatures, any
 * party can recompute the root and confirm the document was built from exactly that state. Leaves
 * are sorted by their canonical string before hashing so ordering cannot change the root, and an
 * odd node is promoted rather than duplicated — duplicating a final leaf is the classic Merkle
 * malleability flaw, where two different leaf sets can produce the same root.
 */
public final class MerkleTree {

    private final List<String> leafHashes;
    private final List<List<String>> levels = new ArrayList<>();

    public MerkleTree(List<String> leafHashes) {
        this.leafHashes = List.copyOf(leafHashes);
        build();
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private void build() {
        if (leafHashes.isEmpty()) {
            levels.add(List.of());
            return;
        }
        List<String> current = new ArrayList<>(leafHashes);
        levels.add(List.copyOf(current));
        while (current.size() > 1) {
            List<String> next = new ArrayList<>();
            for (int i = 0; i < current.size(); i += 2) {
                if (i + 1 < current.size()) {
                    next.add(sha256Hex(current.get(i) + current.get(i + 1)));
                } else {
                    next.add(current.get(i));
                }
            }
            current = next;
            levels.add(List.copyOf(current));
        }
    }

    public String root() {
        List<String> top = levels.get(levels.size() - 1);
        return top.isEmpty() ? sha256Hex("") : top.get(0);
    }

    /** Sibling hashes from leaf to root, each marked left or right, so a leaf can be proven. */
    public List<ProofStep> proofFor(int leafIndex) {
        List<ProofStep> proof = new ArrayList<>();
        int index = leafIndex;
        for (int level = 0; level < levels.size() - 1; level++) {
            List<String> nodes = levels.get(level);
            int siblingIndex = (index % 2 == 0) ? index + 1 : index - 1;
            if (siblingIndex < nodes.size()) {
                proof.add(new ProofStep(nodes.get(siblingIndex), index % 2 == 0 ? "RIGHT" : "LEFT"));
            }
            index /= 2;
        }
        return proof;
    }

    /** Recomputes the root from a leaf and its proof — this is what an auditor runs. */
    public static String recompute(String leafHash, List<ProofStep> proof) {
        String running = leafHash;
        for (ProofStep step : proof) {
            running = step.position().equals("RIGHT")
                    ? sha256Hex(running + step.siblingHash())
                    : sha256Hex(step.siblingHash() + running);
        }
        return running;
    }

    public record ProofStep(String siblingHash, String position) {
    }
}
