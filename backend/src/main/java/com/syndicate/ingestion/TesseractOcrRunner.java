package com.syndicate.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TesseractOcrRunner {

    public List<DocumentToken> run(byte[] pngBytes, int pageNumber, int imageWidth, int imageHeight)
            throws IOException {
        Path tempImage = Files.createTempFile("syndicate-ocr-", ".png");
        try {
            Files.write(tempImage, pngBytes);
            ProcessBuilder builder = new ProcessBuilder("tesseract", tempImage.toString(), "stdout", "tsv", "-l", "eng");
            Process process = builder.start();

            String tsv = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Tesseract timed out after 60 seconds");
            }
            if (process.exitValue() != 0) {
                throw new IOException("Tesseract failed (exit " + process.exitValue() + "): " + stderr);
            }

            return TesseractTsvParser.parse(tsv, pageNumber, imageWidth, imageHeight);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Tesseract process was interrupted", e);
        } finally {
            Files.deleteIfExists(tempImage);
        }
    }
}
