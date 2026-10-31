package com.syndicate.regulatory.engine;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.syndicate.regulatory.engine.dto.TransactionLifecycleTransitionDto;
import com.syndicate.regulatory.engine.dto.TransitionBlockerDto;
import com.syndicate.regulatory.engine.dto.TransitionRequest;
import com.syndicate.regulatory.engine.dto.TransitionResultDto;
import com.syndicate.user.User;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions/{id}/lifecycle")
public class TransactionLifecycleController {

    private final TransactionStateMachineService stateMachineService;
    private final TransactionLifecycleTransitionRepository transitionRepository;

    public TransactionLifecycleController(TransactionStateMachineService stateMachineService,
                                          TransactionLifecycleTransitionRepository transitionRepository) {
        this.stateMachineService = stateMachineService;
        this.transitionRepository = transitionRepository;
    }

    @GetMapping("/blockers")
    public List<TransitionBlockerDto> checkBlockers(@PathVariable UUID id,
                                                   @RequestParam TransactionLifecycleState targetState,
                                                   @AuthenticationPrincipal User currentUser) {
        return stateMachineService.checkTransitionBlockers(id, targetState, currentUser.getId());
    }

    @PostMapping("/transition")
    public TransitionResultDto transition(@PathVariable UUID id,
                                          @Valid @RequestBody TransitionRequest request,
                                          @AuthenticationPrincipal User currentUser) {
        return stateMachineService.transition(id, request, currentUser);
    }

    @GetMapping("/history")
    @Transactional(readOnly = true)
    public List<TransactionLifecycleTransitionDto> getHistory(@PathVariable UUID id) {
        return transitionRepository.findByTransactionIdOrderByTransitionedAtDesc(id).stream()
                .map(TransactionLifecycleTransitionDto::from)
                .toList();
    }
}
