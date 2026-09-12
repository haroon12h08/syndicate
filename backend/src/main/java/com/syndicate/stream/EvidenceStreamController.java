package com.syndicate.stream;

import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Streams document-processing progress over Server-Sent Events so the UI updates as extraction
 * finishes instead of polling.
 *
 * <p>Emitters are held per transaction and pruned on completion, timeout or send failure —
 * a browser tab closing must not leak an emitter for the life of the process.
 */
@RestController
public class EvidenceStreamController {

    private static final Logger log = LoggerFactory.getLogger(EvidenceStreamController.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, List<SseEmitter>> watchers = new ConcurrentHashMap<>();
    private final TransactionService transactionService;

    public EvidenceStreamController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/api/transactions/{id}/events")
    public SseEmitter stream(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        transactionService.requireMembership(id, currentUser.getId());

        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        watchers.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(id, emitter));
        emitter.onTimeout(() -> remove(id, emitter));
        emitter.onError(e -> remove(id, emitter));

        try {
            // An immediate event confirms the stream is live rather than merely open.
            emitter.send(SseEmitter.event().name("connected").data(Map.of("transactionId", id.toString())));
        } catch (IOException e) {
            remove(id, emitter);
        }
        return emitter;
    }

    @EventListener
    public void onEvidenceStatus(EvidenceStatusEvent event) {
        List<SseEmitter> targets = watchers.get(event.transactionId());
        if (targets == null || targets.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : targets) {
            try {
                emitter.send(SseEmitter.event().name("evidence-status").data(event));
            } catch (Exception e) {
                // A dead client is expected; drop it rather than retrying.
                remove(event.transactionId(), emitter);
            }
        }
        log.debug("Streamed {} for evidence {} to {} watcher(s)",
                event.status(), event.evidenceId(), targets.size());
    }

    private void remove(UUID transactionId, SseEmitter emitter) {
        List<SseEmitter> list = watchers.get(transactionId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                watchers.remove(transactionId);
            }
        }
    }
}
