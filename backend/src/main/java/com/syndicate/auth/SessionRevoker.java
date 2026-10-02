package com.syndicate.auth;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Revokes a chain of session tokens in its own transaction.
 *
 * <p>Detecting a replayed token ends in a refusal, and a refusal rolls back the transaction that
 * raised it. The revocation has to outlive that rollback, or the theft would be noticed and then
 * forgotten.
 */
@Component
public class SessionRevoker {

    private final RefreshTokenRepository refreshTokenRepository;

    public SessionRevoker(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(UUID familyId, String reason) {
        refreshTokenRepository.findByFamilyId(familyId).forEach(token -> token.revoke(reason));
    }
}
