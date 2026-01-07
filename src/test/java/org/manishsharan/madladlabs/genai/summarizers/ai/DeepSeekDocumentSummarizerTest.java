package org.manishsharan.madladlabs.genai.summarizers.ai;

import org.junit.jupiter.api.Test;
import org.manishsharan.madladlabs.genai.doc.DocumentExtractor;
import org.manishsharan.madladlabs.genai.summarizers.ai.deepseek.DeepSeekSummarizer;
import org.manishsharan.madladlabs.genai.services.OntologyMethodsSummarizer;
import org.manishsharan.ontology.model.AiEnrichmentPayload;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class DeepSeekDocumentSummarizerTest {

    @Test
    void summarizePdfDocument() throws Exception {
        Path pdfPath = resourcePath("docs/UsingMozillaDeepSpeech .pdf");
        DocumentExtractor.DocumentExtraction extraction = DocumentExtractor.extract(pdfPath);

        OntologyMethodsSummarizer summarizer = DeepSeekSummarizer.getInstance();
        AiEnrichmentPayload payload = summarizer.summarizeDocument(
                "docs/UsingMozillaDeepSpeech .pdf",
                extraction.docType(),
                extraction.title(),
                extraction.datetime(),
                extraction.extractedText()
        );

        assertNotNull(payload, "payload is null");
        assertNotNull(payload.getDocumentEnrichments(), "document enrichments missing");
        assertFalse(payload.getDocumentEnrichments().isEmpty(), "document enrichments empty");

        AiEnrichmentPayload.DocumentEnrichment doc = payload.getDocumentEnrichments().get(0);
        assertNotNull(doc.getSummary(), "document summary missing");
        System.out.println("DeepSeekDocumentSummarizerTest output:");
        System.out.println(doc.getSummary());
    }

    private static Path resourcePath(String resourceName) throws Exception {
        var url = DeepSeekDocumentSummarizerTest.class.getClassLoader().getResource(resourceName);
        assertNotNull(url, "Missing resource: " + resourceName);
        return Path.of(url.toURI());
    }
}
