package com.syndicate.evidence;

import com.syndicate.common.BadRequestException;
import com.syndicate.evidence.scanner.MalwareScanner;
import com.syndicate.evidence.scanner.PassThroughMalwareScanner;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Validates uploaded evidence documents:
 * - Content-length size limits
 * - Magic-byte signature verification
 * - Pluggable malware and virus scanning (ClamAV / EICAR signature detection)
 * - Safe PDF parsing and page-count caps to protect against PDF decompression bombs
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
    private final MalwareScanner malwareScanner;
    private final int maxPdfPages;

    @org.springframework.beans.factory.annotation.Autowired
    public UploadValidator(@Value("${syndicate.storage.max-upload-bytes:26214400}") long maxBytes,
                           MalwareScanner malwareScanner,
                           @Value("${syndicate.storage.max-pdf-pages:2000}") int maxPdfPages) {
        this.maxBytes = maxBytes;
        this.malwareScanner = malwareScanner;
        this.maxPdfPages = maxPdfPages;
    }

    public UploadValidator(long maxBytes) {
        this(maxBytes, new PassThroughMalwareScanner(), 2000);
    }

    public void validate(String fileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BadRequestException("That file is empty.");
        }
        if (content.length > maxBytes) {
            throw new BadRequestException("That file is larger than the " + (maxBytes / 1024 / 1024)
                    + " MB limit. Split it, or upload the relevant extract.");
        }
        String extension = extensionOf(fileName);
        if (!TEXT_EXTENSIONS.contains(extension)) {
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

        // Malware & antivirus scan
        MalwareScanner.ScanResult scanResult = malwareScanner.scan(fileName, content);
        if (!scanResult.isClean()) {
            throw new BadRequestException(scanResult.details());
        }

        // Structural safety check for PDFs (decompression bomb protection & structural corruption)
        if ("pdf".equals(extension)) {
            try (PDDocument doc = Loader.loadPDF(content)) {
                if (doc.getNumberOfPages() > maxPdfPages) {
                    throw new BadRequestException("PDF exceeds maximum allowed page limit of " + maxPdfPages);
                }
            } catch (BadRequestException e) {
                throw e;
            } catch (Exception e) {
                throw new BadRequestException("Corrupt or invalid PDF document: " + e.getMessage());
            }
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
