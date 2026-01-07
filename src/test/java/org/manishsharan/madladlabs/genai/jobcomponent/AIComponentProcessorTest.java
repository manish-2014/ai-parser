package org.manishsharan.madladlabs.genai.jobcomponent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AIComponentProcessorTest {

    @Test
    void shouldSkipKnownDirectories() {
        AIComponentProcessor.IngestionRules rules = AIComponentProcessor.IngestionRules.fromConfig(null);
        assertTrue(AIComponentProcessor.shouldSkipPath(Path.of("node_modules"), true, rules));
        assertTrue(AIComponentProcessor.shouldSkipPath(Path.of("target"), true, rules));
        assertTrue(AIComponentProcessor.shouldSkipPath(Path.of("build"), true, rules));
    }

    @Test
    void shouldNotSkipSourceDirectories() {
        AIComponentProcessor.IngestionRules rules = AIComponentProcessor.IngestionRules.fromConfig(null);
        assertFalse(AIComponentProcessor.shouldSkipPath(Path.of("src/main/java"), true, rules));
    }

    @Test
    void shouldSkipHiddenDirectoriesExceptGithub() {
        AIComponentProcessor.IngestionRules rules = AIComponentProcessor.IngestionRules.fromConfig(null);
        assertTrue(AIComponentProcessor.shouldSkipPath(Path.of(".git"), true, rules));
        assertFalse(AIComponentProcessor.shouldSkipPath(Path.of(".github"), true, rules));
    }

    @Test
    void shouldSelectHtmlTemplateFiles() {
        AIComponentProcessor.IngestionRules rules = AIComponentProcessor.IngestionRules.fromConfig(null);
        assertTrue(AIComponentProcessor.isTemplateFileOfInterest(Path.of("templates/index.html"), rules));
    }
}
