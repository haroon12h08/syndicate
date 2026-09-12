package com.syndicate.drhp.dto;

import java.util.List;

/**
 * Either the compile succeeded and {@code document} is present, or it was refused and
 * {@code findings} says why. The preview is always returned so the team can see how far the
 * document got.
 */
public record CompileResultDto(
        boolean compiled,
        DrhpDocumentDto document,
        List<LintFinding> findings,
        List<CompiledSection> preview
) {
}
