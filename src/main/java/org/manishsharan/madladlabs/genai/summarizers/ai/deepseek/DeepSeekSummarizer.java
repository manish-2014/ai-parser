package org.manishsharan.madladlabs.genai.summarizers.ai.deepseek;

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
import org.manishsharan.madladlabs.genai.services.OntologyMethodsSummarizer;
import org.manishsharan.ontology.model.AiEnrichmentPayload;


import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DeepSeekSummarizer implements OntologyMethodsSummarizer {
    private static final Logger logger = LogManager.getLogger(DeepSeekSummarizer.class);
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private  final String API_KEY;
    final OkHttpClient client ;

    final static  String DEEP_SEEK_MODEL= "deepseek-coder"; //"deepseek-chat";


    private final ObjectMapper oMapper = new ObjectMapper(new JsonFactory());
    static DeepSeekSummarizer deepSeekSummarizer;

    public static DeepSeekSummarizer getInstance(){
        if (deepSeekSummarizer == null) {
            deepSeekSummarizer= new DeepSeekSummarizer();
        }
        return deepSeekSummarizer;
    }

    private  DeepSeekSummarizer() {

        API_KEY = System.getenv("DEEPSEEK_API_KEY");
        if(API_KEY == null){
            throw new RuntimeException("DEEPSEEK_API_KEY environment variable is not set");
        }
        client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)  // Connection establishment
                .readTimeout(90, TimeUnit.SECONDS)     // Server response time
                .writeTimeout(60, TimeUnit.SECONDS)    // Request sending
                .build();
    }
    public String getDeepSeekResponse(String _systemMessage,
                                      String userMessage,
                                      String filePath,
                                      String pipeline) throws IOException {
        // Create the prompt for the DeepSeek model
        //String prompt = "Review this Java code and generate a YAML summary for each method:\n" + fileContent;


        ObjectMapper mapper = new ObjectMapper();
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", DEEP_SEEK_MODEL);
        payload.put("stream", false);
        payload.put("max_tokens", 8192);

        // create response format object
        ObjectNode responseFormatNode = mapper.createObjectNode();
        responseFormatNode.put("type", "json_object");
     //   payload.set("response_format", responseFormatNode);

        ArrayNode messages = mapper.createArrayNode();

        ObjectNode systemMessageNode = mapper.createObjectNode();
        systemMessageNode.put("role", "system");
        systemMessageNode.put("content", _systemMessage);
        messages.add(systemMessageNode);

        // Add the user message
        ObjectNode userMessageNode = mapper.createObjectNode();
        userMessageNode.put("role", "user");
        userMessageNode.put("content", userMessage);
        // userMessageNode.put("max_tokens", 131072);
        messages.add(userMessageNode);
        payload.set("messages", messages);

        String jsonPayload = mapper.writeValueAsString(payload);

        logger.info("DeepSeek request prepared (payload bytes={}) file={} pipeline={}",
                jsonPayload.length(),
                filePath,
                pipeline);
        String requestId = LlmAuditSink.logRequest("deepseek", DEEP_SEEK_MODEL, pipeline, filePath, jsonPayload);
        // Build the HTTP request
        RequestBody body = RequestBody.create(jsonPayload, JSON);
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .post(body)
                .build();

        // Send the request and get the response
        String responseString = null;
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Unexpected response  " + response.toString());
                throw new IOException("Unexpected response code: " + response.code());
            }
            responseString = response.body().string();
            logger.info("DeepSeek response received (bytes={}) file={} pipeline={}",
                    responseString.length(),
                    filePath,
                    pipeline);
            LlmAuditSink.logResponse(requestId, "deepseek", DEEP_SEEK_MODEL, pipeline, filePath, responseString);
            writeDebugArtifact("deepseek-request.json", jsonPayload);
            writeDebugArtifact("deepseek-response.json", responseString);
            return responseString;
        }
    }



    @Override
    public AiEnrichmentPayload summarizeCodeMethods(String relativePath, String language, File javaSource) throws Exception {
        // Read the content of the Java file
        String fileContent = Files.readString(javaSource.toPath());
        // Read the content of the Java file

        ObjectMapper mapper = new ObjectMapper();
        // Create the prompt for the DeepSeek R1 model


        var map = new HashMap<String,String>();
        map.put("language", language);
        map.put("relativeFilePath", relativePath);
        map.put("sourceFileContent", fileContent);
        String userPrompt = TemplateRenderer.getRenderedPrompt(map);
        logger.debug("Deep seek summarizeJavaCodeMethods Prompt: " + userPrompt);
               // "Review this Java code and generate a YAML summary for each method. Do not generate any explanation:\n" + fileContent;
        String systemPrompt = "You are an experienced job software engineer reviewing  code. ";

        // Send the prompt to the DeepSeek R1 API


        String responseString = getDeepSeekResponse(systemPrompt, userPrompt, relativePath, "code");

        logger.debug("DeepSeek Response: " + responseString);


        JsonNode jsonNode = extractResponsePayloadJsonNode(responseString);
        logger.debug("summarizeJavaCodeMethods Response Node content " + jsonNode.toString());
        // Parse the YAML response


        if(jsonNode != null){
            AiEnrichmentPayload payload = AiEnrichmentPayload.fromJson(jsonNode.toString());
            if (payload.getFunctionEnrichments() == null && jsonNode.has("functions")) {
                List<AiEnrichmentPayload.FunctionEnrichment> functions = oMapper
                        .readerForListOf(AiEnrichmentPayload.FunctionEnrichment.class)
                        .readValue(jsonNode.get("functions"));
                payload.setFunctionEnrichments(functions);
            }
            if (payload.getModule() == null) {
                payload.setModule(jsonNode.path("module").asText(null));
            }
            if (payload.getLanguage() == null) {
                payload.setLanguage(jsonNode.path("language").asText(null));
            }
            normalizeEdgeSources(payload);
            payload.setLlmModel(DEEP_SEEK_MODEL);
            payload.setBillableUsage(extractBillableUsage(responseString, relativePath));
            return payload;
        }
        return null;

    }

    @Override
    public AiEnrichmentPayload summarizeGuiTemplate(String relativePath, String language, String content) throws Exception {
        var map = new HashMap<String, String>();
        map.put("language", language);
        map.put("relativeFilePath", relativePath);
        map.put("sourceFileContent", content);
        String userPrompt = TemplateRenderer.renderTemplate(PromptTemplateForGUITemplates.PROMPT_TEMPLATE, map);
        String systemPrompt = "You are an experienced software engineer reviewing GUI templates.";

        String responseString = getDeepSeekResponse(systemPrompt, userPrompt, relativePath, "gui");
        String assistantContent = extractAssistantContent(responseString);
        JsonNode jsonNode = extractTrailingJsonObject(assistantContent);
        if (jsonNode == null) {
            return null;
        }
        AiEnrichmentPayload.TemplateEnrichment template =
                oMapper.treeToValue(jsonNode, AiEnrichmentPayload.TemplateEnrichment.class);
        AiEnrichmentPayload payload = new AiEnrichmentPayload();
        payload.setTemplateEnrichments(List.of(template));
        payload.setLlmModel(DEEP_SEEK_MODEL);
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
        String systemPrompt = "You are an experienced software engineer reviewing configuration files.";

        String responseString = getDeepSeekResponse(systemPrompt, userPrompt, relativePath, "config");
        String assistantContent = extractAssistantContent(responseString);
        if (assistantContent == null || assistantContent.isBlank()) {
            return null;
        }
        assistantContent = assistantContent.replace("```", "").trim();
        AiEnrichmentPayload.ConfigEnrichment config = new AiEnrichmentPayload.ConfigEnrichment();
        config.setPath(relativePath);
        config.setDetectedType(detectedType);
        config.setSummary(assistantContent.trim());
        AiEnrichmentPayload payload = new AiEnrichmentPayload();
        payload.setConfigEnrichments(List.of(config));
        payload.setLlmModel(DEEP_SEEK_MODEL);
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
        String systemPrompt = "You are an experienced software engineer reviewing technical documents.";

        String responseString = getDeepSeekResponse(systemPrompt, userPrompt, relativePath, "document");
        String assistantContent = extractAssistantContent(responseString);
        if (assistantContent == null || assistantContent.isBlank()) {
            return null;
        }
        assistantContent = assistantContent.replace("```", "").trim();
        AiEnrichmentPayload.DocumentEnrichment doc = new AiEnrichmentPayload.DocumentEnrichment();
        doc.setPath(relativePath);
        doc.setDocType(docType);
        doc.setTitle(title);
        doc.setDatetime(datetime);
        doc.setSummary(assistantContent);
        AiEnrichmentPayload payload = new AiEnrichmentPayload();
        payload.setDocumentEnrichments(List.of(doc));
        payload.setLlmModel(DEEP_SEEK_MODEL);
        payload.setBillableUsage(extractBillableUsage(responseString, relativePath));
        return payload;
    }

    public static JsonNode extractResponsePayloadJsonNode(String input) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        if(input == null || input.isEmpty()){
            return null;
        }
        JsonNode root = mapper.readTree(input);
        if(root == null){
            return null;
        }

        String assistantContent = root.path("choices").path(0).path("message").path("content").asText();
        if(assistantContent == null || assistantContent.isEmpty()){
            return null;
        }

        // Remove the triple backticks and extract the JSON content
        String jsonContent = assistantContent.replace("```json", "").replace("```", "").trim();

        // Parse the extracted JSON to verify its validity
        JsonNode extractedJson = mapper.readTree(jsonContent);
       return extractedJson;

    }

    private static AiEnrichmentPayload.BillableUsage extractBillableUsage(String responseString, String filePath) {
        if (responseString == null || responseString.isBlank()) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode root = mapper.readTree(responseString);
            JsonNode usage = root.path("usage");
            if (usage.isMissingNode() || usage.isNull()) {
                return null;
            }

            int inputTokens = usage.path("prompt_tokens").asInt(0);
            int outputTokens = usage.path("completion_tokens").asInt(0);
            int totalTokens = usage.path("total_tokens").asInt(inputTokens + outputTokens);
            int cachedTokens = usage.path("prompt_cache_hit_tokens").asInt(
                    usage.path("prompt_tokens_details").path("cached_tokens").asInt(0));

            AiEnrichmentPayload.BillableUsage billable = new AiEnrichmentPayload.BillableUsage();
            billable.setModel(DEEP_SEEK_MODEL);
            billable.setFilePath(filePath);
            billable.setInputTokens(inputTokens);
            billable.setOutputTokens(outputTokens);
            billable.setCachedTokens(cachedTokens);
            billable.setTotalTokens(totalTokens);
            return billable;
        } catch (IOException e) {
            logger.debug("Unable to parse DeepSeek billable usage: {}", e.getMessage());
            return null;
        }
    }

    private static String extractAssistantContent(String input) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        if (input == null || input.isEmpty()) {
            return null;
        }
        JsonNode root = mapper.readTree(input);
        if (root == null) {
            return null;
        }
        return root.path("choices").path(0).path("message").path("content").asText();
    }

    private static JsonNode extractTrailingJsonObject(String assistantContent) throws IOException {
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
                            if (parsed.has("summary")) {
                                score += 2;
                            }
                            if (parsed.has("file")) {
                                score += 2;
                            }
                            if (parsed.has("endpoints")) {
                                score += 1;
                            }
                            if (score > bestScore) {
                                bestScore = score;
                                bestParsed = parsed;
                            }
                        } catch (IOException ignored) {
                            // continue searching for a valid JSON object
                        }
                        break;
                    }
                }
            }
        }
        return bestParsed;
    }

    private static void writeDebugArtifact(String fileName, String content) {
        String enabled = System.getenv("LLM_DEBUG_OUTPUT");
        if (!"true".equalsIgnoreCase(enabled)) {
            return;
        }
        try {
            Path dir = Paths.get("logs");
            Files.createDirectories(dir);
            Path out = dir.resolve(fileName);
            Files.writeString(out, content, StandardCharsets.UTF_8);
            logger.info("Wrote LLM debug output to {}", out.toAbsolutePath());
        } catch (IOException e) {
            logger.warn("Failed to write LLM debug output: {}", e.getMessage());
        }
    }

    private static void normalizeEdgeSources(AiEnrichmentPayload payload) {
        if (payload == null || payload.getFunctionEnrichments() == null) {
            return;
        }
        for (AiEnrichmentPayload.FunctionEnrichment fn : payload.getFunctionEnrichments()) {
            if (fn == null || fn.getRelationships() == null || fn.getFqn() == null) {
                continue;
            }
            for (AiEnrichmentPayload.Edge edge : fn.getRelationships()) {
                if (edge == null) {
                    continue;
                }
                String source = edge.getSource();
                if (source == null || !source.equals(fn.getFqn())) {
                    edge.setSource(fn.getFqn());
                }
            }
        }
    }

    @Override
    public int getMaxTokens() {
        return 8192;
    }

    @Override
    public int getContextSize() {
        return 65536;
    }

}
