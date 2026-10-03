package com.syndicate.observation;

import com.syndicate.observation.dto.ObservationDto;
import com.syndicate.observation.dto.RecordObservationRequest;
import com.syndicate.observation.dto.RespondRequest;
import com.syndicate.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ObservationController {

    private final ObservationService observationService;

    public ObservationController(ObservationService observationService) {
        this.observationService = observationService;
    }

    @GetMapping("/api/transactions/{id}/observations")
    public List<ObservationDto> list(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return observationService.list(id, currentUser.getId());
    }

    @PostMapping("/api/transactions/{id}/observations")
    @ResponseStatus(HttpStatus.CREATED)
    public ObservationDto record(@PathVariable UUID id, @Valid @RequestBody RecordObservationRequest request,
                                 @AuthenticationPrincipal User currentUser) {
        return observationService.record(id, request, currentUser);
    }

    @PutMapping("/api/observations/{id}/response")
    public ObservationDto respond(@PathVariable UUID id, @Valid @RequestBody RespondRequest request,
                                  @AuthenticationPrincipal User currentUser) {
        return observationService.respond(id, request, currentUser);
    }

    @PostMapping("/api/observations/{id}/response/approve")
    public ObservationDto approve(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return observationService.approveResponse(id, currentUser);
    }

    @PostMapping("/api/observations/{id}/response/sent")
    public ObservationDto markSent(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return observationService.markSent(id, currentUser);
    }

    @PostMapping("/api/observations/{id}/close")
    public ObservationDto close(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return observationService.close(id, currentUser);
    }
}
