package org.manishsharan.madladlabs.genai.summarizers.ai;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.manishsharan.madladlabs.genai.summarizers.ai.deepseek.DeepSeekSummarizer;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.manishsharan.ontology.model.AiEnrichmentPayload;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
public class DeepSeekSummarizerTest {
    private static final Logger logger = LogManager.getLogger(DeepSeekSummarizerTest.class);
    DeepSeekSummarizer summarizer;
    @BeforeEach
    void setUp() {
        // Mock dependencies
        summarizer= DeepSeekSummarizer.getInstance();
    }

    //String testFile="/home/manish/projects/clojure-dev/assetapi/src/clojure/assetapi/dataloader.clj";
    String testFile="/home/manish/projects/clojure-dev/assetapi/src/clojure/assetapi/router.clj";
    @Test
    public void testProcessFile() throws Exception {
        File javaFile = new File(testFile);
        assertNotNull(javaFile);
        assertTrue(javaFile.exists());
        AiEnrichmentPayload payload = summarizer.summarizeCodeMethods("src/clojure/assetapi/router.clj",
                "clojure",
                javaFile);

        assertNotNull(payload, "payload is null");
        assertNotNull(payload.getFunctionEnrichments(), "functions list is null");
        assertFalse(payload.getFunctionEnrichments().isEmpty(), "no functions returned");
        logger.info("DeepSeekSummarizerTest.testProcessFile: functions found = {}", payload.getFunctionEnrichments().size());
        logger.info("DeepSeekSummarizerTest .. payload start\n{}", payload);
        logger.info("DeepSeekSummarizerTest .. payload _____________________________");

        AiEnrichmentPayload.BillableUsage usage = payload.getBillableUsage();
        assertNotNull(usage, "billable usage is null");
        logger.info("DeepSeekSummarizerTest billable: input={}, output={}, cached={}, total={}",
                usage.getInputTokens(),
                usage.getOutputTokens(),
                usage.getCachedTokens(),
                usage.getTotalTokens());

        assertNotNull(payload.getModule(), "module is null");
        assertFalse(payload.getModule().isBlank(), "module is blank");
        assertNotNull(payload.getLanguage(), "language is null");
        assertFalse(payload.getLanguage().isBlank(), "language is blank");

        for (AiEnrichmentPayload.FunctionEnrichment fn : payload.getFunctionEnrichments()) {
            assertNotNull(fn, "function entry is null");
            assertNotNull(fn.getFqn(), "fqn is null");
            assertFalse(fn.getFqn().isBlank(), "fqn is blank");

            assertNotNull(fn.getDescription(), "description is null");
            assertFalse(fn.getDescription().isBlank(), "description is blank");

            List<AiEnrichmentPayload.Edge> rels = fn.getRelationships();
            if (rels != null) {
                for (AiEnrichmentPayload.Edge e : rels) {
                    assertNotNull(e.getType(),   "edge.type is null for " + fn.getFqn());
                    assertNotNull(e.getSource(), "edge.source is null for " + fn.getFqn());
                    assertNotNull(e.getTarget(), "edge.target is null for " + fn.getFqn());
                    // Optional: ensure the edge source matches the function FQN
                    assertEquals(fn.getFqn(), e.getSource(), "edge.source must equal function FQN");
                }
            }
        }


    }
}
