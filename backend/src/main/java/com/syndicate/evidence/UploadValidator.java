package com.syndicate.evidence;

import com.syndicate.common.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What may be uploaded as evidence. Confidential deal documents arrive from many hands, so the
 * product accepts the formats diligence actually uses and refuses everything else by its content,
 * not by the name or the type the browser claims.
 */
@Component
public class UploadValidator {

    /** Extension to the bytes a file of that kind must start with. */
    private static final Map<String, List<byte[]>> SIGNATURES = Map.of(
            "pdf", List.of(new byte[]{'%', 'P', 'D', 'F'}),
            "png", List.of(new byte[]{(byte) 0x89, 'P', 'N', 'G'}),
            "jpg", List.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
            "jpeg", List.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
            "tif", List.of(new byte[]{'I', 'I', 42, 0}, new byte[]{'M', 'M', 0, 42}),
            "tiff", List.of(new byte[]{'I', 'I', 42, 0}, new byte[]{'M', 'M', 0, 42}),
            // Office formats are zip containers
            "xlsx", List.of(new byte[]{'P', 'K', 3, 4}),
            "docx", List.of(new byte[]{'P', 'K', 3, 4}),
            "pptx", List.of(new byte[]{'P', 'K', 3, 4}));

    /** Plain text kinds, which have no signature to check. */
    private static final List<String> TEXT_EXTENSIONS = List.of("txt", "csv", "tsv", "md");

    private final long maxBytes;

    public UploadValidator(@Value("${syndicate.storage.max-upload-bytes:26214400}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public void validate(String fileName, byte[] content) {
        if (content.length == 0) {
            throw new BadRequestException("That file is empty.");
        }
        if (content.length > maxBytes) {
            throw new BadRequestException("That file is larger than the " + (maxBytes / 1024 / 1024)
                    + " MB limit. Split it, or upload the relevant extract.");
        }
        String extension = extensionOf(fileName);
        if (TEXT_EXTENSIONS.contains(extension)) {
            return;
        }
        List<byte[]> expected = SIGNATURES.get(extension);
        if (expected == null) {
            throw new BadRequestException("Syndicate takes PDFs, images, and Office or text files. "
                    + "\"" + fileName + "\" is not one of those.");
        }
        if (expected.stream().noneMatch(signature -> startsWith(content, signature))) {
            throw new BadRequestException("\"" + fileName + "\" is not really a " + extension.toUpperCase(Locale.ROOT)
                    + " file. Upload the original document.");
        }
    }

    private static String extensionOf(String fileName) {
        String name = fileName == null ? "" : fileName;
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
