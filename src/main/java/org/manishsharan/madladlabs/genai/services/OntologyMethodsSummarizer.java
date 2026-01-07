package org.manishsharan.madladlabs.genai.services;

import org.manishsharan.ontology.model.AiEnrichmentPayload;

import java.io.File;

public interface OntologyMethodsSummarizer {

   int getMaxTokens();
   int getContextSize();
   // List<MethodSummary> parseMethodSummaryResponse(String jsonResponse) throws IOException;
   AiEnrichmentPayload summarizeCodeMethods(String relativePath, String language, File javaSource) throws  Exception;
   AiEnrichmentPayload summarizeGuiTemplate(String relativePath, String language, String content) throws Exception;
   AiEnrichmentPayload summarizeConfigTemplate(String relativePath, String detectedType, String content) throws Exception;
   AiEnrichmentPayload summarizeDocument(String relativePath,
                                         String docType,
                                         String title,
                                         String datetime,
                                         String extractedContent) throws Exception;
}
