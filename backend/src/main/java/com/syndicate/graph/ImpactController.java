package com.syndicate.graph;

import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ImpactController {

    private final DependencyGraphService graph;
    private final ImpactService impactService;
    private final TransactionService transactionService;

    public ImpactController(DependencyGraphService graph, ImpactService impactService,
                            TransactionService transactionService) {
        this.graph = graph;
        this.impactService = impactService;
        this.transactionService = transactionService;
    }

    /** What a change to this fact would reach, before anyone changes it. */
    @GetMapping("/api/facts/{id}/impact")
    public ImpactService.ImpactSet impact(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return impactService.forFact(id, currentUser.getId());
    }

    /** Everything that depends, directly or transitively, on the given node. */
    @GetMapping("/api/transactions/{id}/dependents")
    @Transactional(readOnly = true)
    public List<ImpactedNode> dependents(@PathVariable UUID id, @RequestParam GraphNodeType type,
                                         @RequestParam UUID lineageId, @AuthenticationPrincipal User currentUser) {
        transactionService.requireMembership(id, currentUser.getId());
        return graph.impactOf(id, new GraphNode(type, lineageId));
    }
}
