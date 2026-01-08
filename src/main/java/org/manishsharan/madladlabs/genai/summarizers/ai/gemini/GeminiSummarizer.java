package org.manishsharan.madladlabs.genai.summarizers.ai.gemini;


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
import org.manishsharan.ontology.llmdto.JavaClassSummary;
import org.manishsharan.ontology.llmdto.MethodSummary;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GeminiSummarizer implements OntologyMethodsSummarizer {

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static GeminiSummarizer instance;
    private final OkHttpClient client;
    private static  String API_KEY;
    private static String MODEL_NAME = "gemini-2.5-flash-lite";
    Logger logger = LogManager.getLogger(GeminiSummarizer.class);

    private GeminiSummarizer() {
        API_KEY = System.getenv("GEMINI_API_KEY");
        if (API_KEY == null) {
            throw new RuntimeException("GEMINI_API_KEY environment variable is not set");
        }

        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized GeminiSummarizer getInstance() {
        if (instance == null) {
            instance = new GeminiSummarizer();
        }
        return instance;
    }
    public GeminiResponse invokeLLM(String promptForllm, String filePath, String pipeline) throws IOException {
        // Create the request body as before
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode requestBody = mapper.createObjectNode();
        String url = API_URL ;
        // "contents" array
        ArrayNode contentsNode = requestBody.putArray("contents");
        ObjectNode contentObject = contentsNode.addObject();
        ArrayNode partsArray = contentObject.putArray("parts");
        ObjectNode textNode = partsArray.addObject();

        textNode.put("text", promptForllm);

        // "generationConfig" object
        ObjectNode generationConfig = requestBody.putObject("generationConfig");
        generationConfig.put("temperature", 1);
        generationConfig.put("topK", 40);
        generationConfig.put("topP", 0.95);
        generationConfig.put("maxOutputTokens", 8192);

        // Tells Gemini to return JSON as text, matching schema
        generationConfig.put("response_mime_type", "application/json");

        // "response_schema" object
        ObjectNode responseSchema = generationConfig.putObject("response_schema");
        responseSchema.put("type", "OBJECT");

        // Top-level properties: "functions" array
        ObjectNode topLevelProperties = responseSchema.putObject("properties");

        //--- need language and module properties too
        topLevelProperties.putObject("module").put("type", "STRING");
        topLevelProperties.putObject("language").put("type", "STRING");

        ObjectNode functionsArrayNode = topLevelProperties.putObject("functions");
        functionsArrayNode.put("type", "ARRAY");

        // "functions" array items
        ObjectNode functionItemsNode = functionsArrayNode.putObject("items");
        functionItemsNode.put("type", "OBJECT");
        ObjectNode functionItemProps = functionItemsNode.putObject("properties");

        // Properties for each function object
        functionItemProps.putObject("fqn").put("type", "STRING");
        functionItemProps.putObject("description").put("type", "STRING");

        // "relationships" array
        ObjectNode relationshipsNode = functionItemProps.putObject("relationships");
        relationshipsNode.put("type", "ARRAY");
        ObjectNode relationshipsItems = relationshipsNode.putObject("items");
        relationshipsItems.put("type", "OBJECT");
        ObjectNode relationshipsProps = relationshipsItems.putObject("properties");
        relationshipsProps.putObject("type").put("type", "STRING");
        relationshipsProps.putObject("source").put("type", "STRING");
        relationshipsProps.putObject("target").put("type", "STRING");

        // required fields for relationships array items
        ArrayNode relationshipsRequired = relationshipsItems.putArray("required");
        relationshipsRequired.add("type");
        relationshipsRequired.add("source");
        relationshipsRequired.add("target");

        // required fields for each item in "functions"
        ArrayNode functionItemRequired = functionItemsNode.putArray("required");
        functionItemRequired.add("fqn");
        functionItemRequired.add("description");
        functionItemRequired.add("relationships");

        // top-level required fields
        ArrayNode topLevelRequired = responseSchema.putArray("required");
        topLevelRequired.add("functions");
        topLevelRequired.add("module");
        topLevelRequired.add("language");

        // Convert request to JSON string
        String requestJson = mapper.writeValueAsString(requestBody);
        logger.info("Gemini request prepared (payload bytes={}) file={} pipeline={}",
                requestJson.length(),
                filePath,
                pipeline);
        String requestId = LlmAuditSink.logRequest("gemini", MODEL_NAME, pipeline, filePath, requestJson);

        // Make the HTTP request
        //OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create(
                requestJson,
                MediaType.parse("application/json; charset=utf-8")
        );
        Request request = new Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            String rawResponse = response.body().string();
            logger.info("Gemini response received (bytes={}) file={} pipeline={}",
                    rawResponse.length(),
                    filePath,
                    pipeline);
            LlmAuditSink.logResponse(requestId, "gemini", MODEL_NAME, pipeline, filePath, rawResponse);
            return extractCandidateResponse(mapper, rawResponse);
        }
    }

    private GeminiResponse invokeLLMFreeform(String promptForllm,
                                             String responseMimeType,
                                             String filePath,
                                             String pipeline) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode requestBody = mapper.createObjectNode();
        String url = API_URL;
        ArrayNode contentsNode = requestBody.putArray("contents");
        ObjectNode contentObject = contentsNode.addObject();
        ArrayNode partsArray = contentObject.putArray("parts");
        ObjectNode textNode = partsArray.addObject();
        textNode.put("text", promptForllm);

        ObjectNode generationConfig = requestBody.putObject("generationConfig");
        generationConfig.put("temperature", 1);
        generationConfig.put("topK", 40);
        generationConfig.put("topP", 0.95);
        generationConfig.put("maxOutputTokens", 8192);
        if (responseMimeType != null) {
            generationConfig.put("response_mime_type", responseMimeType);
        }

        String requestJson = mapper.writeValueAsString(requestBody);
        logger.info("Gemini request prepared (payload bytes={}) file={} pipeline={}",
                requestJson.length(),
                filePath,
                pipeline);
        String requestId = LlmAuditSink.logRequest("gemini", MODEL_NAME, pipeline, filePath, requestJson);
        RequestBody body = RequestBody.create(
                requestJson,
                MediaType.parse("application/json; charset=utf-8")
        );
        Request request = new Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            String rawResponse = response.body().string();
            logger.info("Gemini response received (bytes={}) file={} pipeline={}",
                    rawResponse.length(),
                    filePath,
                    pipeline);
            LlmAuditSink.logResponse(requestId, "gemini", MODEL_NAME, pipeline, filePath, rawResponse);
            return extractCandidateResponse(mapper, rawResponse);
        }
    }

    private static GeminiResponse extractCandidateResponse(ObjectMapper mapper, String rawResponse) throws IOException {
        JsonNode root = mapper.readTree(rawResponse);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.size() == 0) {
            throw new IOException("No candidates returned from Gemini.");
        }
        JsonNode firstCandidate = candidates.get(0);
        JsonNode contentNode = firstCandidate.path("content");
        JsonNode partsNode = contentNode.path("parts");
        if (!partsNode.isArray() || partsNode.size() == 0) {
            throw new IOException("No 'parts' found in Gemini response.");
        }
        String text = partsNode.get(0).path("text").asText();
        JsonNode usageMetadata = root.path("usageMetadata");
        return new GeminiResponse(text, usageMetadata.isMissingNode() ? null : usageMetadata);
    }






    /**
     * Parses the PaLM API JSON response into a List of MethodSummary objects.
     *
     * @param jsonResponse the entire JSON payload returned from the PaLM API
     * @return a List of MethodSummary objects
     * @throws IOException if JSON parsing fails
     */
    public  List<MethodSummary> parseMethodSummaryResponse(String jsonResponse) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root = objectMapper.readTree(jsonResponse);

        // 1) Locate "candidates" array
        JsonNode candidatesNode = root.path("candidates");
        if (!candidatesNode.isArray() || candidatesNode.size() == 0) {
            // No candidates or invalid shape
            return new ArrayList<>();
        }

        // 2) Extract the first candidate -> content -> parts -> 0 -> text
        JsonNode contentNode = candidatesNode.get(0).path("content");
        JsonNode partsNode = contentNode.path("parts");
        if (!partsNode.isArray() || partsNode.size() == 0) {
            // No parts array or empty
            return new ArrayList<>();
        }

        // This is the JSON string containing {"response": [...]}
        String innerJsonString = partsNode.get(0).path("text").asText();
        if (innerJsonString.isEmpty()) {
            // No text found
            return new ArrayList<>();
        }

        // 3) Parse the inner JSON string as an object
        JsonNode innerRoot = objectMapper.readTree(innerJsonString);

        // The "response" array from the inner JSON
        JsonNode responseArray = innerRoot.path("response");
        if (!responseArray.isArray()) {
            // "response" is missing or not an array
            return new ArrayList<>();
        }

        // 4) Convert each item in "response" into a MethodSummary object
        List<MethodSummary> methodSummaries = new ArrayList<>();
        for (JsonNode item : responseArray) {
            MethodSummary ms = new MethodSummary();

            // Map the fields:
            //   "method" -> methodName
            //   "parent" -> parent
            //   "methodSummary" -> methodSummary
            ms.setMethodName(item.path("method").asText());

            ms.setMethodSummary(item.path("methodSummary").asText());

            methodSummaries.add(ms);
        }

        return methodSummaries;
    }

    @Override
    public int getMaxTokens() {
        return 65536;
    }

    @Override
    public int getContextSize() {
        return 1048576;
    }

    /*

      public AiEnrichmentPayload summarizeCodeMethods(String relativePath, String language, File javaSource) throws Exception {




     */
    @Override
    public AiEnrichmentPayload summarizeCodeMethods(String relativePath, String language, File javaSource) throws Exception {
        // Read the content of the Java file
        String fileContent = Files.readString(javaSource.toPath());
        ObjectMapper mapper = new ObjectMapper();
        // Create the prompt for the DeepSeek R1 model


        var map = new HashMap<String,String>();
        map.put("language", language);
        map.put("relativeFilePath", relativePath);
        map.put("sourceFileContent", fileContent);
        String userPrompt = TemplateRenderer.getRenderedPrompt(map);
        logger.debug("summarizeJavaCodeMethods Prompt: " + userPrompt);
        //String responseString = invokeLLM(userPrompt);
        GeminiResponse response = invokeLLM(userPrompt, relativePath, "code");
        String responseString = response.getContent();

        logger.debug("summarizeJavaCodeMethods Response: " + responseString);
        System.out.println("summarizeJavaCodeMethods Response: " + responseString);
        JsonNode responseNode= mapper.readTree(responseString);
        logger.debug("summarizeJavaCodeMethods Response Node content: " + responseNode.toString());
        // help complete this method to return a json not
        if(responseNode != null){
            AiEnrichmentPayload payload = AiEnrichmentPayload.fromJson(responseNode.toString());
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
            payload.setLlmModel(MODEL_NAME);
            payload.setBillableUsage(extractBillableUsage(response.getUsageMetadata(), relativePath));
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
        GeminiResponse response = invokeLLMFreeform(userPrompt, "application/json", relativePath, "gui");
        String responseString = response.getContent();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(responseString);
        AiEnrichmentPayload.TemplateEnrichment template =
                mapper.treeToValue(jsonNode, AiEnrichmentPayload.TemplateEnrichment.class);
        AiEnrichmentPayload payload = new AiEnrichmentPayload();
        payload.setTemplateEnrichments(List.of(template));
        payload.setLlmModel(MODEL_NAME);
        payload.setBillableUsage(extractBillableUsage(response.getUsageMetadata(), relativePath));
        return payload;
    }

    @Override
    public AiEnrichmentPayload summarizeConfigTemplate(String relativePath, String detectedType, String content) throws Exception {
        var map = new HashMap<String, String>();
        map.put("detectedType", detectedType);
        map.put("relativeFilePath", relativePath);
        map.put("sourceFileContent", content);
        String userPrompt = TemplateRenderer.renderTemplate(PromptTemplateForConfigTemplates.PROMPT_TEMPLATE, map);
        GeminiResponse response = invokeLLMFreeform(userPrompt, "text/plain", relativePath, "config");
        String responseString = response.getContent();
        if (responseString == null || responseString.isBlank()) {
            return null;
        }
        responseString = responseString.replace("```", "").trim();
        AiEnrichmentPayload.ConfigEnrichment config = new AiEnrichmentPayload.ConfigEnrichment();
        config.setPath(relativePath);
        config.setDetectedType(detectedType);
        config.setSummary(responseString.trim());
        AiEnrichmentPayload payload = new AiEnrichmentPayload();
        payload.setConfigEnrichments(List.of(config));
        payload.setLlmModel(MODEL_NAME);
        payload.setBillableUsage(extractBillableUsage(response.getUsageMetadata(), relativePath));
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
        GeminiResponse response = invokeLLMFreeform(userPrompt, "text/plain", relativePath, "document");
        String responseString = response.getContent();
        if (responseString == null || responseString.isBlank()) {
            return null;
        }
        responseString = responseString.replace("```", "").trim();
        AiEnrichmentPayload.DocumentEnrichment doc = new AiEnrichmentPayload.DocumentEnrichment();
        doc.setPath(relativePath);
        doc.setDocType(docType);
        doc.setTitle(title);
        doc.setDatetime(datetime);
        doc.setSummary(responseString);
        AiEnrichmentPayload payload = new AiEnrichmentPayload();
        payload.setDocumentEnrichments(List.of(doc));
        payload.setLlmModel(MODEL_NAME);
        payload.setBillableUsage(extractBillableUsage(response.getUsageMetadata(), relativePath));
        return payload;
    }

    private static AiEnrichmentPayload.BillableUsage extractBillableUsage(JsonNode usageMetadata, String filePath) {
        if (usageMetadata == null || usageMetadata.isMissingNode() || usageMetadata.isNull()) {
            return null;
        }
        int inputTokens = usageMetadata.path("promptTokenCount").asInt(0);
        int outputTokens = usageMetadata.path("candidatesTokenCount").asInt(0);
        int totalTokens = usageMetadata.path("totalTokenCount").asInt(inputTokens + outputTokens);

        AiEnrichmentPayload.BillableUsage billable = new AiEnrichmentPayload.BillableUsage();
        billable.setModel(MODEL_NAME);
        billable.setFilePath(filePath);
        billable.setInputTokens(inputTokens);
        billable.setOutputTokens(outputTokens);
        billable.setCachedTokens(0);
        billable.setTotalTokens(totalTokens);
        return billable;
    }

    public static class GeminiResponse {
        private final String content;
        private final JsonNode usageMetadata;

        public GeminiResponse(String content, JsonNode usageMetadata) {
            this.content = content;
            this.usageMetadata = usageMetadata;
        }

        public String getContent() {
            return content;
        }

        public JsonNode getUsageMetadata() {
            return usageMetadata;
        }
    }
}
