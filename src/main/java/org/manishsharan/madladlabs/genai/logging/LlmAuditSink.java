package org.manishsharan.madladlabs.genai.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.manishsharan.ontology.model.AiEnrichmentPayload;

import java.util.UUID;

public final class LlmAuditSink {
    private static final Logger logger = LogManager.getLogger(LlmAuditSink.class);

    private LlmAuditSink() {}

    public static String logRequest(String provider,
                                    String model,
                                    String pipeline,
                                    String filePath,
                                    String requestBody) {
        String requestId = UUID.randomUUID().toString();
        logger.info(
                "LLM_REQUEST id={} provider={} model={} pipeline={} file={}\n{}",
                requestId,
                safe(provider),
                safe(model),
                safe(pipeline),
                safe(filePath),
                requestBody == null ? "" : requestBody
        );
        publishToClickHouse("request", requestId, provider, model, pipeline, filePath, requestBody, null);
        return requestId;
    }

    public static void logResponse(String requestId,
                                   String provider,
                                   String model,
                                   String pipeline,
                                   String filePath,
                                   String responseBody) {
        logger.info(
                "LLM_RESPONSE id={} provider={} model={} pipeline={} file={}\n{}",
                safe(requestId),
                safe(provider),
                safe(model),
                safe(pipeline),
                safe(filePath),
                responseBody == null ? "" : responseBody
        );
        publishToClickHouse("response", requestId, provider, model, pipeline, filePath, null, responseBody);
    }

    public static void logUsage(String requestId,
                                String provider,
                                String model,
                                String pipeline,
                                String filePath,
                                AiEnrichmentPayload.BillableUsage usage) {
        if (usage == null) {
            return;
        }
        logger.info(
                "LLM_USAGE id={} provider={} model={} pipeline={} file={} input={} output={} cached={} total={}",
                safe(requestId),
                safe(provider),
                safe(model),
                safe(pipeline),
                safe(filePath),
                usage.getInputTokens(),
                usage.getOutputTokens(),
                usage.getCachedTokens(),
                usage.getTotalTokens()
        );
        publishToClickHouse("usage", requestId, provider, model, pipeline, filePath, null, null);
    }

    private static void publishToClickHouse(String recordType,
                                            String requestId,
                                            String provider,
                                            String model,
                                            String pipeline,
                                            String filePath,
                                            String requestBody,
                                            String responseBody) {
        // TODO: Replace this with a ClickHouse client call.
        // This method is intentionally a no-op to serve as a shim.
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
