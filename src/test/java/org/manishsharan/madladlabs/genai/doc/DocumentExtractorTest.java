package org.manishsharan.madladlabs.genai.doc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentExtractorTest {
    private static final Logger logger = LogManager.getLogger(DocumentExtractorTest.class);

    @Test
    void extractsMarkdownText() throws Exception {
        Path path = resourcePath("docs/GITEA-PROXMOX-LXC-README.md");
        DocumentExtractor.DocumentExtraction extraction = DocumentExtractor.extract(path);
        assertEquals("md", extraction.docType());
        String s =extraction.extractedText();
        assertNotNull(s);
        logger.info("extraction.extractedText() {}", s);
        assertTrue(extraction.extractedText().contains("Gitea LXC Server Setup"));
    }

    @Test
    void extractsPdfText() throws Exception {
        Path path = resourcePath("docs/UsingMozillaDeepSpeech .pdf");
        DocumentExtractor.DocumentExtraction extraction = DocumentExtractor.extract(path);
        assertEquals("pdf", extraction.docType());
        String s =extraction.extractedText();
        assertNotNull(s);
        logger.info("extraction.extractedText() {}", s);
        assertTrue(extraction.extractedText().length() > 50);
    }

    private static Path resourcePath(String resourceName) throws Exception {
        var url = DocumentExtractorTest.class.getClassLoader().getResource(resourceName);
        assertNotNull(url, "Missing resource: " + resourceName);
        return Path.of(url.toURI());
    }
}
