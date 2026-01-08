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
import org.manishsharan.madladlabs.genai.logging.LlmAuditSink;
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

    private final static  String ANTHROPIC_VERSION="anthropic-version: 2023-06-01";
    private final static  String HAIKU="claude-haiku-4-5-20251001";
    private final static  String SONNET="claude-sonnet-4-5-20250929";
    private final static  String OPUS="claude-opus-4-5-20251101";

    private final static Integer MAX_TOKENS=20000;
    private final static  String CLAUDE_MODEL=HAIKU;

    private HaikuSummarizer() {
        API_KEY = System.getenv("ANTHROPIC_API_KEY");
        this.client = new OkHttpClient.Builder()
                .connectTimeout(90, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .callTimeout(180, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized HaikuSummarizer getInstance() {
        if (instance == null) {
            instance = new HaikuSummarizer();
        }
        return instance;
    }

    public String invokeLLM(String userPrompt, String filePath, String pipeline) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode payload = mapper.createObjectNode();
        //claude-3-5-haiku-20241022
        payload.put("model", CLAUDE_MODEL);
        payload.put("max_tokens", MAX_TOKENS);
        payload.put("temperature", 1);
        ObjectNode message = mapper.createObjectNode();
        message.put("role", "user");
        ArrayNode content = mapper.createArrayNode();
        ObjectNode textNode = mapper.createObjectNode();
        textNode.put("type", "text");
        textNode.put("text", userPrompt);
        content.add(textNode);
        message.set("content", content);
        payload.putArray("messages").add(message);

        RequestBody body = RequestBody.create(payload.toString(), JSON);

        logger.info("Anthropic request prepared (payload bytes={}) file={} pipeline={}",
                payload.toString().length(),
                filePath,
                pipeline);
        String requestId = LlmAuditSink.logRequest("anthropic", CLAUDE_MODEL, pipeline, filePath, payload.toString());

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
            logger.info("Anthropic response received (bytes={}) file={} pipeline={}",
                    responseBody.length(),
                    filePath,
                    pipeline);
            LlmAuditSink.logResponse(requestId, "anthropic", CLAUDE_MODEL, pipeline, filePath, responseBody);
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


        String responseString = invokeLLM(userPrompt, relativePath, "code");

        logger.debug("summarizeJavaCodeMethods Response: " + responseString);

        AiEnrichmentPayload payload= extractResponse( responseString);
        if (payload != null) {
            payload.setBillableUsage(extractBillableUsage(responseString, relativePath));
        }
        return payload;
    }

    @Override
    public AiEnrichmentPayload summarizeGuiTemplate(String relativePath, String language, String content) throws Exception {
        var map = new HashMap<String, String>();
        map.put("language", language);
        map.put("relativeFilePath", relativePath);
        map.put("sourceFileContent", content);
        String userPrompt = TemplateRenderer.renderTemplate(PromptTemplateForGUITemplates.PROMPT_TEMPLATE, map);
        String responseString = invokeLLM(userPrompt, relativePath, "gui");
        String assistantText = extractAssistantText(responseString);
        if (assistantText == null || assistantText.isBlank()) {
            return null;
        }
        JsonNode jsonNode = extractStructuredJson(assistantText, "file", "summary");
        if (jsonNode == null) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        AiEnrichmentPayload.TemplateEnrichment template =
                mapper.treeToValue(jsonNode, AiEnrichmentPayload.TemplateEnrichment.class);
        AiEnrichmentPayload payload = new AiEnrichmentPayload();
        payload.setTemplateEnrichments(List.of(template));
        payload.setLlmModel(CLAUDE_MODEL);
        payload.setBillableUsage(extractBillableUsage(responseString, relativePath));
        return payload;
    }

    @Override
    public AiEnrichmentPayload summarizeConfigTemplate(String relativePath, String detectedType, String content) throws Exception {
        var map = new HashMap<String, String>();
        map.put("detectedType", detectedType);
        map.put("relativeFilePath", relativePath);
        map.put("sourceFileContent", content);
        String userPrompt = TemplateRenderer.renderTemplate(PromptTemplateForConfigTemplates.PROMPT_TEMPLATE, map);
        String responseString = invokeLLM(userPrompt, relativePath, "config");
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
        payload.setBillableUsage(extractBillableUsage(responseString, relativePath));
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
        String responseString = invokeLLM(userPrompt, relativePath, "document");
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
        payload.setBillableUsage(extractBillableUsage(responseString, relativePath));
        return payload;
    }

    public static  AiEnrichmentPayload extractResponse(String responseString) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonResponse = mapper.readTree(responseString);


        JsonNode contentArray = jsonResponse.path("content");

        if (contentArray.isArray()) {
            for (JsonNode contentNode : contentArray) {
                if (contentNode.has("text")) {
                    JsonNode responseNode = extractStructuredJson(contentNode.get("text").asText(),
                            "functions", "module");
                    if (responseNode == null) {
                        continue;
                    }
                    logger.debug("summarizeJavaCodeMethods Response Node: " + responseNode.toString());
                    AiEnrichmentPayload payload= AiEnrichmentPayload.fromJson(responseNode.toString());
                    if (payload.getFunctionEnrichments() == null && responseNode.has("functions")) {
                        List<AiEnrichmentPayload.FunctionEnrichment> functions = mapper
                                .readerForListOf(AiEnrichmentPayload.FunctionEnrichment.class)
                                .readValue(responseNode.get("functions"));
                        payload.setFunctionEnrichments(functions);
                    }
                    if (payload.getModule() == null) {
                        payload.setModule(responseNode.path("module").asText(null));
                    }
                    if (payload.getLanguage() == null) {
                        payload.setLanguage(responseNode.path("language").asText(null));
                    }
                    payload.setLlmModel(CLAUDE_MODEL);
                    return payload;

                }
            }
        }
        return null;

    }

    private static AiEnrichmentPayload.BillableUsage extractBillableUsage(String responseString, String filePath) {
        if (responseString == null || responseString.isBlank()) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode jsonResponse = mapper.readTree(responseString);
            JsonNode usage = jsonResponse.path("usage");
            if (usage.isMissingNode() || usage.isNull()) {
                return null;
            }

            int inputTokens = usage.path("input_tokens").asInt(0);
            int outputTokens = usage.path("output_tokens").asInt(0);
            int cachedTokens = usage.path("cache_creation_input_tokens").asInt(0)
                    + usage.path("cache_read_input_tokens").asInt(0);
            int totalTokens = inputTokens + outputTokens;

            AiEnrichmentPayload.BillableUsage billable = new AiEnrichmentPayload.BillableUsage();
            billable.setModel(CLAUDE_MODEL);
            billable.setFilePath(filePath);
            billable.setInputTokens(inputTokens);
            billable.setOutputTokens(outputTokens);
            billable.setCachedTokens(cachedTokens);
            billable.setTotalTokens(totalTokens);
            return billable;
        } catch (IOException e) {
            logger.debug("Unable to parse Anthropic billable usage: {}", e.getMessage());
            return null;
        }
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

    private static JsonNode extractStructuredJson(String assistantContent, String... requiredKeys) throws IOException {
        if (assistantContent == null || assistantContent.isBlank()) {
            return null;
        }
        String cleaned = assistantContent.replace("```json", "").replace("```", "").trim();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode bestParsed = null;
        int bestScore = -1;

        for (int i = 0; i < cleaned.length(); i++) {
            if (cleaned.charAt(i) != '{') {
                continue;
            }
            int depth = 0;
            boolean inString = false;
            boolean escape = false;
            for (int j = i; j < cleaned.length(); j++) {
                char c = cleaned.charAt(j);
                if (inString) {
                    if (escape) {
                        escape = false;
                    } else if (c == '\\') {
                        escape = true;
                    } else if (c == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (c == '"') {
                    inString = true;
                    continue;
                }
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        String candidate = cleaned.substring(i, j + 1).trim();
                        try {
                            JsonNode parsed = mapper.readTree(candidate);
                            int score = 0;
                            if (requiredKeys != null) {
                                for (String key : requiredKeys) {
                                    if (parsed.has(key)) {
                                        score += 2;
                                    }
                                }
                            }
                            if (score > bestScore) {
                                bestScore = score;
                                bestParsed = parsed;
                            }
                        } catch (IOException ignored) {
                        }
                        break;
                    }
                }
            }
        }
        return bestParsed;
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
