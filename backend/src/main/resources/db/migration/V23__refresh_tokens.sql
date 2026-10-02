-- Sessions that can be ended (spec §51; plan H2).
--
-- The access token is short-lived and never stored by the browser; the long-lived half is a
-- random secret held in an httpOnly cookie and only ever stored here as a hash. Each use rotates
-- it, so a stolen copy is detectable: presenting a rotated token revokes the whole chain.
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id UUID NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    revoked_at TIMESTAMP,
    revoked_reason VARCHAR(255),
    user_agent VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id, expires_at);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
