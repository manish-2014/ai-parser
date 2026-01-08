package org.manishsharan.madladlabs.genai.jobcomponent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.manishsharan.madladlabs.genai.summarizers.ai.PromptTemplateForGUITemplates;
import org.manishsharan.madladlabs.genai.summarizers.ai.TemplateRenderer;
import org.manishsharan.madladlabs.genai.summarizers.ai.anthropic.HaikuSummarizer;
import org.manishsharan.madladlabs.genai.summarizers.ai.deepseek.DeepSeekSummarizer;
import org.manishsharan.madladlabs.genai.summarizers.ai.gemini.GeminiSummarizer;
import org.manishsharan.ontology.job.config.FileIngestionConfig;
import org.manishsharan.ontology.model.AiEnrichmentPayload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GuiTemplateSummarizerTest {
    private static final Logger logger = LogManager.getLogger(GuiTemplateSummarizerTest.class);
    private static final String RELATIVE_PATH = "src/test/resources/test-misc/encodingjobslist.html";
    private static final Path TEMPLATE_PATH = Paths.get(RELATIVE_PATH);

    @Test
    public void deepSeekSummarizesGuiTemplateEncodingJobsList() throws Exception {
        String content = readTemplateContent();
        assertGuiPipelineAndPrompt(content);

        DeepSeekSummarizer summarizer = DeepSeekSummarizer.getInstance();
        AiEnrichmentPayload payload = summarizer.summarizeGuiTemplate(RELATIVE_PATH, "html", content);
        assertGuiPayload(payload, "deepseek");
    }

    @Test
    public void haikuSummarizesGuiTemplateEncodingJobsList() throws Exception {
        String content = readTemplateContent();
        assertGuiPipelineAndPrompt(content);

        HaikuSummarizer summarizer = HaikuSummarizer.getInstance();
        AiEnrichmentPayload payload = summarizer.summarizeGuiTemplate(RELATIVE_PATH, "html", content);
        assertGuiPayload(payload, "anthropic");
    }

    @Test
    public void geminiSummarizesGuiTemplateEncodingJobsList() throws Exception {
        String content = readTemplateContent();
        assertGuiPipelineAndPrompt(content);

        GeminiSummarizer summarizer = GeminiSummarizer.getInstance();
        AiEnrichmentPayload payload = summarizer.summarizeGuiTemplate(RELATIVE_PATH, "html", content);
        assertGuiPayload(payload, "gemini");
    }

    private static String readTemplateContent() throws Exception {
        assertTrue(Files.exists(TEMPLATE_PATH), "GUI template fixture not found: " + TEMPLATE_PATH);
        return Files.readString(TEMPLATE_PATH);
    }

    private static void assertGuiPipelineAndPrompt(String content) throws Exception {
        AIComponentProcessor.IngestionRules rules =
                AIComponentProcessor.IngestionRules.fromConfig(new FileIngestionConfig());
        assertTrue(AIComponentProcessor.isTemplateFileOfInterest(TEMPLATE_PATH, rules),
                "encodingjobslist.html should be treated as GUI template");

        Map<String, String> map = new HashMap<>();
        map.put("language", "html");
        map.put("relativeFilePath", RELATIVE_PATH);
        map.put("sourceFileContent", content);
        String prompt = TemplateRenderer.renderTemplate(PromptTemplateForGUITemplates.PROMPT_TEMPLATE, map);
        assertNotNull(prompt, "GUI prompt is null");
        assertTrue(prompt.contains(RELATIVE_PATH), "Prompt should include the relative file path");
        assertTrue(prompt.contains("List of all Video Encoding Tasks"),
                "Prompt should include GUI heading text");
        assertTrue(prompt.contains("app.userid"),
                "Prompt should include userid placeholder context");
        assertTrue(prompt.contains("app.paginatedDataUrl"),
                "Prompt should include dataUrl placeholder context");
    }

    private static void assertGuiPayload(AiEnrichmentPayload payload, String provider) {
        assertNotNull(payload, provider + " payload is null");
        assertNotNull(payload.getTemplateEnrichments(), provider + " template enrichments is null");
        assertFalse(payload.getTemplateEnrichments().isEmpty(), provider + " template enrichments is empty");

        AiEnrichmentPayload.TemplateEnrichment template = payload.getTemplateEnrichments().get(0);
        assertNotNull(template, provider + " template enrichment is null");
        assertNotNull(template.getSummary(), provider + " summary is null");
        assertFalse(template.getSummary().isBlank(), provider + " summary is blank");

        AiEnrichmentPayload.TemplateFile file = template.getFile();
        assertNotNull(file, provider + " template file metadata is null");
        if (file.getPath() != null && !file.getPath().isBlank()) {
            assertTrue(file.getPath().contains("encodingjobslist.html"),
                    provider + " template path should reference encodingjobslist.html");
        }

        assertNotNull(file.getType(), provider + " template file type is null");
        assertFalse(file.getType().isBlank(), provider + " template file type is blank");

        assertNotNull(file.getLanguage(), provider + " template language is null");
        assertFalse(file.getLanguage().isBlank(), provider + " template language is blank");
        assertTrue(isLikelyTemplateLanguage(file.getLanguage()),
                provider + " unexpected template language: " + file.getLanguage());

        String summaryLower = template.getSummary().toLowerCase(Locale.ROOT);
        assertTrue(summaryLower.contains("encoding"), provider + " summary should mention encoding");
        assertTrue(summaryLower.contains("pagination")
                        || summaryLower.contains("paginator")
                        || summaryLower.contains("paginationnavcontainer"),
                provider + " summary should mention pagination");
        assertTrue(summaryLower.contains("userid") || summaryLower.contains("dataurl"),
                provider + " summary should mention template placeholders like userid/dataUrl");

        AiEnrichmentPayload.BillableUsage usage = payload.getBillableUsage();
        assertNotNull(usage, provider + " billable usage is null");
        logger.info("GUI summary {} billable: input={}, output={}, cached={}, total={}",
                provider,
                usage.getInputTokens(),
                usage.getOutputTokens(),
                usage.getCachedTokens(),
                usage.getTotalTokens());
    }

    private static boolean isLikelyTemplateLanguage(String language) {
        String normalized = language.toLowerCase(Locale.ROOT);
        return normalized.equals("html")
                || normalized.equals("handlebars")
                || normalized.equals("mustache")
                || normalized.equals("jinja")
                || normalized.equals("unknown");
    }
}
