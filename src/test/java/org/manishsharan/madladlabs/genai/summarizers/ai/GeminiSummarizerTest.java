package org.manishsharan.madladlabs.genai.summarizers.ai;
import org.junit.jupiter.api.BeforeEach;
import org.manishsharan.madladlabs.genai.summarizers.ai.gemini.GeminiSummarizer;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.manishsharan.madladlabs.genai.services.OntologyMethodsSummarizer;
import org.manishsharan.ontology.model.AiEnrichmentPayload;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class GeminiSummarizerTest
{
    OntologyMethodsSummarizer summarizer;
    @BeforeEach
    void setUp() {
        // Mock dependencies
        summarizer= GeminiSummarizer.getInstance();
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

        assertNotNull(payload.getModule(), "module is null");
        assertFalse(payload.getModule().isBlank(), "module is blank");
        assertNotNull(payload.getLanguage(), "language is null");
        assertFalse(payload.getLanguage().isBlank(), "language is blank");

        assertNotNull(payload, "payload is null");
        assertNotNull(payload.getFunctionEnrichments(), "functions list is null");
        assertFalse(payload.getFunctionEnrichments().isEmpty(), "no functions returned");

        for (AiEnrichmentPayload.FunctionEnrichment fn : payload.getFunctionEnrichments()) {
            assertNotNull(fn, "function entry is null");
            assertNotNull(fn.getFqn(), "fqn is null");
            assertFalse(fn.getFqn().isBlank(), "fqn is blank");

            assertNotNull(fn.getDescription(), "description is null");
            assertFalse(fn.getDescription().isBlank(), "description is blank");

            System.out.println("GeminiSummarizerTest .. payload start");
            System.out.println(payload);
            System.out.println("GeminiSummarizerTest .. payload _____________________________");


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
