package org.manishsharan.madladlabs.genai.summarizers.ai;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.manishsharan.madladlabs.genai.summarizers.ai.anthropic.HaikuSummarizer;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.manishsharan.ontology.model.AiEnrichmentPayload;
import org.manishsharan.madladlabs.genai.services.OntologyMethodsSummarizer;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HaikuSummarizerTest {

    private static final Logger logger = LogManager.getLogger(HaikuSummarizerTest.class);

    OntologyMethodsSummarizer summarizer;
    @BeforeEach
    void setUp() {
        // Mock dependencies
        summarizer= HaikuSummarizer.getInstance();
    }
    String testFile="/home/manish/projects/clojure-dev/assetapi/src/clojure/assetapi/dataloader.clj";

    @Test
    public void testProcessFile() throws Exception {
        File javaFile = new File(testFile);
        assertNotNull(javaFile);
        assertTrue(javaFile.exists());
        AiEnrichmentPayload payload = summarizer.summarizeCodeMethods("src/clojure/assetapi/dataloader.clj",
                "clojure",
                javaFile);

        assertNotNull(payload, "payload is null");
        assertNotNull(payload.getFunctionEnrichments(), "functions list is null");
        assertFalse(payload.getFunctionEnrichments().isEmpty(), "no functions returned");

        System.out.println("HaikuSummarizerTest .. payload start");
        System.out.println(payload);
        System.out.println("HaikuTest .. payload _____________________________");

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
