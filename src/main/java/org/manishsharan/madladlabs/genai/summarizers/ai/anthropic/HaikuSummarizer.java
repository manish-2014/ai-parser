package org.manishsharan.madladlabs.genai.summarizers.ai.anthropic;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.manishsharan.madladlabs.genai.summarizers.ai.PromptUtils;
import org.manishsharan.madladlabs.genai.summarizers.ai.PromptTemplateForConfigTemplates;
import org.manishsharan.madladlabs.genai.summarizers.ai.PromptTemplateForDocuments;
import org.manishsharan.madladlabs.genai.summarizers.ai.PromptTemplateForGUITemplates;
import org.manishsharan.madladlabs.genai.summarizers.ai.TemplateRenderer;

import org.manishsharan.ontology.llmdto.JavaClassSummary;
import org.manishsharan.ontology.llmdto.MethodSummary;

import org.manishsharan.madladlabs.genai.services.OntologyMethodsSummarizer;
import org.manishsharan.ontology.model.AiEnrichmentPayload;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HaikuSummarizer  implements OntologyMethodsSummarizer {

    static Logger logger = LogManager.getLogger(HaikuSummarizer.class);
    public String summarize(String text) {
        return "Haiku: " + text;
    }
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static  String API_KEY ;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static HaikuSummarizer instance;
    private final OkHttpClient client;

    private final static  String CLAUDE_MODEL="claude-haiku-4-5-20251001";

    private HaikuSummarizer() {
        API_KEY = System.getenv("ANTHROPIC_API_KEY");
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized HaikuSummarizer getInstance() {
        if (instance == null) {
            instance = new HaikuSummarizer();
        }
        return instance;
    }

    public String invokeLLM(String userPrompt) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode payload = mapper.createObjectNode();
        //claude-3-5-haiku-20241022
        payload.put("model", "claude-3-5-sonnet-20241022");
        payload.put("max_tokens", 4096);
        ObjectNode message = mapper.createObjectNode();
        message.put("role", "user");
        message.put("content", userPrompt);
        payload.putArray("messages").add(message);

        RequestBody body = RequestBody.create(payload.toString(), JSON);

        logger.debug("invokeLLM Request: " + payload.toPrettyString());

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", API_KEY)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            logger.debug("invokeLLM Response: " + response.toString());
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected HTTP response: " + response.code() + " - " + response.message());
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            JsonNode jsonResponse = mapper.readTree(responseBody);
            return jsonResponse.toPrettyString();
        }
    }


    @Override
    public AiEnrichmentPayload summarizeCodeMethods(String relativePath, String language, File javaSource) throws Exception {

        String fileContent = Files.readString(javaSource.toPath());
        ObjectMapper mapper = new ObjectMapper();
        // Create the prompt for the DeepSeek R1 model


        var map = new HashMap<String,String>();
        map.put("language", language);
        map.put("relativeFilePath", relativePath);
        map.put("sourceFileContent", fileContent);
        String userPrompt = TemplateRenderer.getRenderedPrompt(map);


        String responseString = invokeLLM( userPrompt);

        logger.debug("summarizeJavaCodeMethods Response: " + responseString);

        AiEnrichmentPayload payload= extractResponse( responseString);
        return payload;
    }

    @Override
    public AiEnrichmentPayload summarizeGuiTemplate(String relativePath, String language, String content) throws Exception {
        var map = new HashMap<String, String>();
        map.put("language", language);
        map.put("relativeFilePath", relativePath);
        map.put("sourceFileContent", content);
        String userPrompt = TemplateRenderer.renderTemplate(PromptTemplateForGUITemplates.PROMPT_TEMPLATE, map);
        String responseString = invokeLLM(userPrompt);
        String assistantText = extractAssistantText(responseString);
        if (assistantText == null || assistantText.isBlank()) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(assistantText);
        AiEnrichmentPayload.TemplateEnrichment template =
                mapper.treeToValue(jsonNode, AiEnrichmentPayload.TemplateEnrichment.class);
        AiEnrichmentPayload payload = new AiEnrichmentPayload();
        payload.setTemplateEnrichments(List.of(template));
        payload.setLlmModel(CLAUDE_MODEL);
        return payload;
    }

    @Override
    public AiEnrichmentPayload summarizeConfigTemplate(String relativePath, String detectedType, String content) throws Exception {
        var map = new HashMap<String, String>();
        map.put("detectedType", detectedType);
        map.put("relativeFilePath", relativePath);
        map.put("sourceFileContent", content);
        String userPrompt = TemplateRenderer.renderTemplate(PromptTemplateForConfigTemplates.PROMPT_TEMPLATE, map);
        String responseString = invokeLLM(userPrompt);
        String assistantText = extractAssistantText(responseString);
        if (assistantText == null || assistantText.isBlank()) {
            return null;
        }
        assistantText = assistantText.replace("```", "").trim();
        AiEnrichmentPayload.ConfigEnrichment config = new AiEnrichmentPayload.ConfigEnrichment();
        config.setPath(relativePath);
        config.setDetectedType(detectedType);
        config.setSummary(assistantText.trim());
        AiEnrichmentPayload payload = new AiEnrichmentPayload();
        payload.setConfigEnrichments(List.of(config));
        payload.setLlmModel(CLAUDE_MODEL);
        return payload;
    }

    @Override
    public AiEnrichmentPayload summarizeDocument(String relativePath,
                                                 String docType,
                                                 String title,
                                                 String datetime,
                                                 String extractedContent) throws Exception {
        var map = new HashMap<String, String>();
        map.put("sourceDocType", docType);
        map.put("sourceDocTitle", title != null ? title : "");
        map.put("sourceDocTime", datetime != null ? datetime : "");
        map.put("sourceFileExtractedContent", extractedContent != null ? extractedContent : "");
        String userPrompt = TemplateRenderer.renderTemplate(PromptTemplateForDocuments.PROMPT_TEMPLATE, map);
        String responseString = invokeLLM(userPrompt);
        String assistantText = extractAssistantText(responseString);
        if (assistantText == null || assistantText.isBlank()) {
            return null;
        }
        assistantText = assistantText.replace("```", "").trim();
        AiEnrichmentPayload.DocumentEnrichment doc = new AiEnrichmentPayload.DocumentEnrichment();
        doc.setPath(relativePath);
        doc.setDocType(docType);
        doc.setTitle(title);
        doc.setDatetime(datetime);
        doc.setSummary(assistantText);
        AiEnrichmentPayload payload = new AiEnrichmentPayload();
        payload.setDocumentEnrichments(List.of(doc));
        payload.setLlmModel(CLAUDE_MODEL);
        return payload;
    }

    public static  AiEnrichmentPayload extractResponse(String responseString) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonResponse = mapper.readTree(responseString);


        JsonNode contentArray = jsonResponse.path("content");

        if (contentArray.isArray()) {
            for (JsonNode contentNode : contentArray) {
                if (contentNode.has("text")) {
                    JsonNode responseNode= mapper.readTree(contentNode.get("text").asText());
                    logger.debug("summarizeJavaCodeMethods Response Node: " + responseNode.toString());
                    AiEnrichmentPayload payload= AiEnrichmentPayload.fromJson(responseNode.toString());
                    payload.setLlmModel(CLAUDE_MODEL);
                    return payload;

                }
            }
        }
        return null;

    }

    private static String extractAssistantText(String responseString) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonResponse = mapper.readTree(responseString);
        JsonNode contentArray = jsonResponse.path("content");
        if (contentArray.isArray()) {
            for (JsonNode contentNode : contentArray) {
                if (contentNode.has("text")) {
                    return contentNode.get("text").asText();
                }
            }
        }
        return null;
    }

    @Override
    public int getMaxTokens() {
        return 65536;
    }

    @Override
    public int getContextSize() {
        return 200000;
    }
}
