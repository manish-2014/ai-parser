package org.manishsharan.madladlabs.genai.jobcomponent;

import org.junit.jupiter.api.Test;
import org.manishsharan.madladlabs.genai.summarizers.ai.deepseek.DeepSeekSummarizer;
import org.manishsharan.ontology.model.AiEnrichmentPayload;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class GuiTemplatePipelineTest {

    @Test
    void templateFileIsDetectedByPipeline() throws Exception {
        URL url = getClass().getClassLoader().getResource("test-misc/encodingjobslist.html");
        assertNotNull(url, "encodingjobslist.html resource missing");
        Path path = Paths.get(url.toURI());

        AIComponentProcessor.IngestionRules rules = AIComponentProcessor.IngestionRules.fromConfig(null);
        assertTrue(AIComponentProcessor.isTemplateFileOfInterest(path, rules), "template file not detected");
        assertFalse(AIComponentProcessor.isCodeFileOfInterest(path, rules), "template should not be code");
        assertFalse(AIComponentProcessor.isConfigFileOfInterest(path, rules), "template should not be config");
        assertFalse(AIComponentProcessor.isDocumentFileOfInterest(path, rules), "template should not be document");
    }

    @Test
    void deepSeekSummarizesGuiTemplate() throws Exception {
        URL url = getClass().getClassLoader().getResource("test-misc/encodingjobslist.html");
        assertNotNull(url, "encodingjobslist.html resource missing");
        Path path = Paths.get(url.toURI());
        String relativePath = "test-misc/encodingjobslist.html";
        String content = java.nio.file.Files.readString(path);

        DeepSeekSummarizer summarizer = DeepSeekSummarizer.getInstance();
        AiEnrichmentPayload payload = summarizer.summarizeGuiTemplate(relativePath, "html", content);

        assertNotNull(payload, "payload is null");
        assertNotNull(payload.getTemplateEnrichments(), "template enrichments missing");
        assertFalse(payload.getTemplateEnrichments().isEmpty(), "no template enrichments returned");

        AiEnrichmentPayload.TemplateEnrichment template = payload.getTemplateEnrichments().get(0);
        if (template.getFile() != null) {
            assertEquals("template", template.getFile().getType(), "unexpected template type");
            String language = template.getFile().getLanguage();
            assertNotNull(language, "template language missing");
            assertTrue(language.equalsIgnoreCase("html") || language.equalsIgnoreCase("handlebars"),
                    "unexpected template language");
        }

        String summary = template.getSummary();
        assertNotNull(summary, "summary is null");
        assertFalse(summary.isBlank(), "summary is blank");
        String summaryLower = summary.toLowerCase();
        assertTrue(summaryLower.contains("video encoding"), "summary missing page intent");
        boolean hasPagination = summaryLower.contains("pagination")
                || summaryLower.contains("paginationpanel")
                || summaryLower.contains("paginationnavcontainer");
        assertTrue(hasPagination, "summary missing pagination references");
        boolean hasAssets = summaryLower.contains("encodingjobslistbb-mvc.js")
                || summaryLower.contains("paginator-mvc.js")
                || summaryLower.contains("foundation.min.js");
        assertTrue(hasAssets, "summary missing asset references");
    }
}
