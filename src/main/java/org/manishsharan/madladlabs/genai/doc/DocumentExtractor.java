package org.manishsharan.madladlabs.genai.doc;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class DocumentExtractor {
    private static final int MAX_EXTRACT_CHARS = 200_000;
    private static final Tika TIKA = new Tika();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

    private DocumentExtractor() {}

    public static DocumentExtraction extract(Path path) throws Exception {
        Metadata metadata = new Metadata();
        BodyContentHandler handler = new BodyContentHandler(MAX_EXTRACT_CHARS);
        AutoDetectParser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();

        try (InputStream input = Files.newInputStream(path)) {
            parser.parse(input, handler, metadata, context);
        }

        String content = handler.toString();
        String title = firstNonBlank(
                metadata.get(TikaCoreProperties.TITLE),
                metadata.get("title"),
                metadata.get("dc:title")
        );
        String datetime = firstNonBlank(
                metadata.get(TikaCoreProperties.MODIFIED),
                metadata.get(TikaCoreProperties.CREATED),
                metadata.get("Last-Modified")
        );
        if (datetime == null) {
            datetime = ISO.format(Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()));
        }
        String docType = detectDocType(path, metadata);

        return new DocumentExtraction(docType, title, datetime, content);
    }

    private static String detectDocType(Path path, Metadata metadata) {
        String extension = extensionOf(path);
        if (!extension.isBlank()) {
            return extension;
        }
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        if (contentType == null || contentType.isBlank()) {
            try {
                contentType = TIKA.detect(path);
            } catch (Exception ignored) {
                return "unknown";
            }
        }
        int slash = contentType.indexOf('/');
        if (slash >= 0 && slash < contentType.length() - 1) {
            return contentType.substring(slash + 1);
        }
        return contentType;
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return "";
        }
        return name.substring(idx + 1).toLowerCase();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public record DocumentExtraction(String docType,
                                     String title,
                                     String datetime,
                                     String extractedText) {}
}
