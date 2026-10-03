package com.syndicate.diligence;

import com.syndicate.diligence.dto.AnswerQuestionRequest;
import com.syndicate.diligence.dto.DiligenceQuestionDto;
import com.syndicate.issue.IssueSeverity;
import com.syndicate.user.User;
import com.syndicate.workstream.WorkstreamType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class DiligenceController {

    public record AddQuestionRequest(String question, WorkstreamType workstreamType, IssueSeverity severity) {
    }

    private final DiligenceService diligenceService;

    public DiligenceController(DiligenceService diligenceService) {
        this.diligenceService = diligenceService;
    }

    @GetMapping("/api/transactions/{id}/diligence-questions")
    public List<DiligenceQuestionDto> list(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return diligenceService.list(id, currentUser.getId());
    }

    @PostMapping("/api/transactions/{id}/diligence-questions")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public DiligenceQuestionDto add(@PathVariable UUID id, @RequestBody AddQuestionRequest request,
                                    @AuthenticationPrincipal User currentUser) {
        return diligenceService.add(id, request.question(), request.workstreamType(), request.severity(), currentUser);
    }

    @PutMapping("/api/diligence-questions/{id}/answer")
    public DiligenceQuestionDto answer(@PathVariable UUID id, @Valid @RequestBody AnswerQuestionRequest request,
                                       @AuthenticationPrincipal User currentUser) {
        return diligenceService.answer(id, request, currentUser);
    }

    @PostMapping("/api/diligence-questions/{id}/accept")
    public DiligenceQuestionDto accept(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return diligenceService.accept(id, currentUser);
    }
}
