package org.manishsharan.madladlabs.genai.jobcomponent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.manishsharan.madladlabs.genai.doc.DocumentExtractor;
import org.manishsharan.madladlabs.genai.summarizers.ai.deepseek.DeepSeekSummarizer;
import org.manishsharan.ontology.job.config.Component;
import org.manishsharan.ontology.job.config.FileIngestionConfig;
import org.manishsharan.ontology.job.config.JobConfig;
import org.manishsharan.ontology.job.config.Solution;
import org.manishsharan.ontology.listener.OntologyListener;
import org.manishsharan.ontology.service.ComponentProcessor;
import org.manishsharan.ontology.service.FileValidator;
import org.manishsharan.madladlabs.genai.services.OntologyMethodsSummarizer;
import org.manishsharan.ontology.model.AiEnrichmentPayload;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class AIComponentProcessor implements ComponentProcessor {

    private static final Logger logger = LogManager.getLogger(AIComponentProcessor.class);

    /**
     * Switchable summarizer provider. Keep this as a single place to change later.
     * Supported here: "deepseek" (default). Add more branches in buildSummarizer().
     */
    private  final String SUMMARIZER_PROVIDER ;

    /** Logical processor key to store against FileValidator (prevents rework). */
    private  final String PROCESSOR_KEY ;
    private final IngestionRules ingestionRules;
    private int concurrency = 2;

    public AIComponentProcessor(JobConfig jobConfig) {
        // Constructor can be extended to accept dependencies if needed.

        if(jobConfig.getSummarizerConfig().getSummarizationModel()==null) throw new IllegalArgumentException("Summarization model not specified in JobConfig");
        if(jobConfig.getSummarizerConfig().getSummarizationModel().isEmpty()) throw new IllegalArgumentException("Summarization model is empty in JobConfig");
        if(jobConfig.getSummarizerConfig().getEmbeddingModel()==null) throw new IllegalArgumentException("Embedding model not specified in SummarizerConfig");
        if(jobConfig.getSummarizerConfig().getEmbeddingModel().isEmpty()) throw new IllegalArgumentException("Embedding model is empty in SummarizerConfig");

        SUMMARIZER_PROVIDER = jobConfig.getSummarizerConfig().getSummarizationModel()!=null?
                jobConfig.getSummarizerConfig().getSummarizationModel():"deepseek";
        PROCESSOR_KEY = "job:" + SUMMARIZER_PROVIDER;
        ingestionRules = IngestionRules.fromConfig(jobConfig.getFileIngestionConfig());

    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        if (concurrency < 1) {
            throw new IllegalArgumentException("concurrency must be >= 1");
        }
        this.concurrency = concurrency;
    }

    @Override
    public void processComponent(Solution solution,
                                 Component component,
                                 OntologyListener listener,
                                 FileValidator fileValidator) {

        if (component == null ||
                solution == null || solution.getName().isEmpty() ||
                component.getName() == null || component.getName().isEmpty()) {
            logger.error("Component information incomplete, cannot process. solution='{}', component='{}'",
                    solution.getName(), component);
            return;
        }

        logger.info("AIComponentProcessor: Processing component: {}", component);

        final String codeBasePath = component.getCodeBasePath();
        if (codeBasePath == null || codeBasePath.isEmpty()) {
            final String msg = "Code base path is not specified for component: " + component.getName();
            logger.error(msg);
            listener.error(msg, null);
            return;
        }

        final Path repoRoot = Paths.get(codeBasePath);
        if (!Files.isDirectory(repoRoot)) {
            final String msg = "Code base path is not a directory: " + repoRoot;
            logger.error(msg);
            listener.error(msg, null);
            return;
        }

        final OntologyMethodsSummarizer summarizer;
        try {
            summarizer = buildSummarizer(SUMMARIZER_PROVIDER);
        } catch (IllegalArgumentException ex) {
            logger.error("Failed to initialize summarizer '{}': {}", SUMMARIZER_PROVIDER, ex.getMessage(), ex);
            listener.error("Failed to initialize summarizer: " + ex.getMessage(), ex);
            return;
        }

        // Walk repo and process eligible files
        final AtomicInteger filesSeen = new AtomicInteger();
        final AtomicInteger codeSeen = new AtomicInteger();
        final AtomicInteger templateSeen = new AtomicInteger();
        final AtomicInteger configSeen = new AtomicInteger();
        final AtomicInteger docSeen = new AtomicInteger();
        final AtomicInteger summarized = new AtomicInteger();

        final Map<Path, String> assetCache = new ConcurrentHashMap<>();
        final Map<String, BillableTotals> billableTotals = new ConcurrentHashMap<>();
        final ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        final CompletionService<Void> completionService = new ExecutorCompletionService<>(executor);
        final AtomicInteger tasksSubmitted = new AtomicInteger();
        try {
            Files.walkFileTree(repoRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (Files.isSymbolicLink(dir)) {
                        logger.debug("Skipping symlinked directory: {}", dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (shouldSkipPath(dir, true, ingestionRules)) {
                        logger.debug("Skipping directory: {}", dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (Files.isSymbolicLink(path)) {
                        logger.debug("Skipping symlinked file: {}", path);
                        return FileVisitResult.CONTINUE;
                    }
                    if (shouldSkipPath(path, false, ingestionRules)) {
                        logger.debug("Skipping file: {}", path);
                        return FileVisitResult.CONTINUE;
                    }

                    final String relPath = repoRoot.relativize(path).toString();
                    final String fileName = path.getFileName().toString();
                    boolean isCode = isCodeFileOfInterest(path, ingestionRules);
                    boolean isTemplate = isTemplateFileOfInterest(path, ingestionRules);
                    boolean isConfig = isConfigFileOfInterest(path, ingestionRules);
                    boolean isDocument = isDocumentFileOfInterest(path, ingestionRules);
                    if (!isCode && !isTemplate && !isConfig && !isDocument) {
                        return FileVisitResult.CONTINUE;
                    }

                    filesSeen.incrementAndGet();

                    try {
                        if (isTooLarge(path, ingestionRules)) {
                            logger.debug("Skipping large file ({} bytes): {}", safeSize(path), relPath);
                            AiEnrichmentPayload payload = new AiEnrichmentPayload();
                            payload.setComponent(component.getName());
                            payload.setSolution(solution.getName());
                            payload.setFileNotes(List.of(new AiEnrichmentPayload.FileNote(relPath, "Skipped due to size limit")));
                            listener.processLLMEnrichment(solution, component, relPath, payload);
                            fileValidator.aiParseCompleted(solution.getName(), component.getName(), relPath, path.toFile());
                            return FileVisitResult.CONTINUE;
                        }

                        if (isCode) {
                            codeSeen.incrementAndGet();
                            tasksSubmitted.incrementAndGet();
                            completionService.submit(() -> {
                                try {
                                    processCodeFile(repoRoot, solution, component, listener, fileValidator, summarizer, path, billableTotals);
                                    summarized.incrementAndGet();
                                } catch (Exception ex) {
                                    logger.error("AI summarization failed for {}: {}", relPath, ex.getMessage(), ex);
                                    listener.error("AI summarization failed for " + relPath + ": " + ex.getMessage(), ex);
                                }
                                return null;
                            });
                            return FileVisitResult.CONTINUE;
                        }
                        if (isTemplate) {
                            templateSeen.incrementAndGet();
                            tasksSubmitted.incrementAndGet();
                            completionService.submit(() -> {
                                try {
                                    processTemplateFile(repoRoot, solution, component, listener, fileValidator, summarizer, path, assetCache, billableTotals);
                                    summarized.incrementAndGet();
                                } catch (Exception ex) {
                                    logger.error("AI summarization failed for {}: {}", relPath, ex.getMessage(), ex);
                                    listener.error("AI summarization failed for " + relPath + ": " + ex.getMessage(), ex);
                                }
                                return null;
                            });
                            return FileVisitResult.CONTINUE;
                        }
                        if (isConfig) {
                            configSeen.incrementAndGet();
                            tasksSubmitted.incrementAndGet();
                            completionService.submit(() -> {
                                try {
                                    processConfigFile(repoRoot, solution, component, listener, fileValidator, summarizer, path, fileName, billableTotals);
                                    summarized.incrementAndGet();
                                } catch (Exception ex) {
                                    logger.error("AI summarization failed for {}: {}", relPath, ex.getMessage(), ex);
                                    listener.error("AI summarization failed for " + relPath + ": " + ex.getMessage(), ex);
                                }
                                return null;
                            });
                            return FileVisitResult.CONTINUE;
                        }
                        if (isDocument) {
                            docSeen.incrementAndGet();
                            tasksSubmitted.incrementAndGet();
                            completionService.submit(() -> {
                                try {
                                    processDocumentFile(repoRoot, solution, component, listener, fileValidator, summarizer, path, billableTotals);
                                    summarized.incrementAndGet();
                                } catch (Exception ex) {
                                    logger.error("AI summarization failed for {}: {}", relPath, ex.getMessage(), ex);
                                    listener.error("AI summarization failed for " + relPath + ": " + ex.getMessage(), ex);
                                }
                                return null;
                            });
                        }
                    } catch (Exception ex) {
                        logger.error("AI summarization failed for {}: {}", relPath, ex.getMessage(), ex);
                        listener.error("AI summarization failed for " + relPath + ": " + ex.getMessage(), ex);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            for (int i = 0; i < tasksSubmitted.get(); i++) {
                try {
                    Future<Void> future = completionService.take();
                    future.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    logger.error("Interrupted while waiting for file processing tasks.");
                    break;
                } catch (ExecutionException ex) {
                    logger.error("File processing task failed: {}", ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage(), ex);
                }
            }

            logger.info("AIComponentProcessor: Done. Files seen={}, eligible={}, summarized={}",
                    filesSeen.get(),
                    codeSeen.get() + templateSeen.get() + configSeen.get() + docSeen.get(),
                    summarized.get());
            if (!billableTotals.isEmpty()) {
                for (Map.Entry<String, BillableTotals> entry : billableTotals.entrySet()) {
                    BillableTotals totals = entry.getValue();
                    logger.info(
                            "Billable usage summary [{}] files={}, input={}, output={}, cached={}, total={}",
                            entry.getKey(),
                            totals.files.sum(),
                            totals.inputTokens.sum(),
                            totals.outputTokens.sum(),
                            totals.cachedTokens.sum(),
                            totals.totalTokens.sum()
                    );
                }
            }

        } catch (IOException ioEx) {
            logger.error("Error traversing code base: {}", ioEx.getMessage(), ioEx);
            listener.error("Error traversing code base: " + ioEx.getMessage(), ioEx);
        } finally {
            executor.shutdown();
        }
    }

    // ---------- Helpers ----------

    static boolean isCodeFileOfInterest(Path p, IngestionRules rules) {
        final String ext = extensionOf(p.getFileName().toString());
        return rules.codeExtensions.contains(ext);
    }

    static boolean isTemplateFileOfInterest(Path p, IngestionRules rules) {
        final String ext = extensionOf(p.getFileName().toString());
        return rules.templateExtensions.contains(ext);
    }

    static boolean isConfigFileOfInterest(Path p, IngestionRules rules) {
        final String name = p.getFileName().toString();
        if (rules.configFileNames.contains(name)) {
            return true;
        }
        if (name.startsWith(".env.")) {
            return true;
        }
        final String ext = extensionOf(name);
        return rules.configExtensions.contains(ext);
    }

    static boolean isDocumentFileOfInterest(Path p, IngestionRules rules) {
        final String ext = extensionOf(p.getFileName().toString());
        return rules.documentExtensions.contains(ext);
    }

    static boolean shouldSkipPath(Path p, boolean isDirectory, IngestionRules rules) {
        final String name = p.getFileName() != null ? p.getFileName().toString() : p.toString();
        if (rules.skipHidden && name.startsWith(".")) {
            if (isDirectory && rules.allowHiddenDirectories.contains(name)) {
                return false;
            }
            return true;
        }
        if (isDirectory) {
            return rules.skipDirectoryNames.contains(name);
        }
        if (rules.skipFileNames.contains(name)) {
            return true;
        }
        if (rules.skipMinified && (name.endsWith(".min.js") || name.endsWith(".min.css"))) {
            return true;
        }
        final String ext = extensionOf(name);
        return rules.skipFileExtensions.contains(ext);
    }

    private static String languageFromExtension(Path p) {
        final String n = p.getFileName().toString().toLowerCase();
        if (n.endsWith(".java")) return "java";
        if (n.endsWith(".py"))   return "python";
        if (n.endsWith(".clj"))  return "clojure";
        return "unknown";
    }

    private static String templateLanguageFromExtension(Path p) {
        final String n = p.getFileName().toString().toLowerCase();
        if (n.endsWith(".html") || n.endsWith(".htm")) return "html";
        if (n.endsWith(".jinja") || n.endsWith(".j2") || n.endsWith(".jinja2")) return "jinja";
        if (n.endsWith(".twig")) return "twig";
        if (n.endsWith(".hbs") || n.endsWith(".handlebars")) return "handlebars";
        if (n.endsWith(".mustache")) return "mustache";
        if (n.endsWith(".ejs")) return "ejs";
        if (n.endsWith(".erb")) return "erb";
        if (n.endsWith(".jsp") || n.endsWith(".jspx")) return "jsp";
        if (n.endsWith(".ftl")) return "freemarker";
        if (n.endsWith(".vm")) return "velocity";
        if (n.endsWith(".thymeleaf")) return "thymeleaf";
        if (n.endsWith(".liquid")) return "liquid";
        if (n.endsWith(".jrxml")) return "jrxml";
        if (n.endsWith(".rdl")) return "rdl";
        if (n.endsWith(".rpt")) return "rpt";
        return "unknown";
    }

    private static String configTypeFromExtension(Path p) {
        final String n = p.getFileName().toString().toLowerCase();
        if (n.endsWith(".env")) return "env";
        if (n.endsWith(".properties")) return "properties";
        if (n.endsWith(".conf") || n.endsWith(".cfg") || n.endsWith(".ini")) return "conf";
        if (n.endsWith(".toml")) return "toml";
        if (n.endsWith(".yaml") || n.endsWith(".yml")) return "yaml";
        if (n.endsWith(".json")) return "json";
        if (n.endsWith(".xml")) return "xml";
        if (n.endsWith(".txt")) return "requirements";
        return "unknown";
    }

    private static String extensionOf(String name) {
        int idx = name.lastIndexOf('.');
        if (idx < 0) {
            return "";
        }
        return name.substring(idx).toLowerCase();
    }

    private static boolean isTooLarge(Path path, IngestionRules rules) {
        if (rules.maxFileSizeBytes <= 0) {
            return false;
        }
        try {
            return Files.size(path) > rules.maxFileSizeBytes;
        } catch (IOException e) {
            logger.debug("Unable to read file size for {}: {}", path, e.getMessage());
            return false;
        }
    }

    private static long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1L;
        }
    }

    private void processCodeFile(Path repoRoot,
                                 Solution solution,
                                 Component component,
                                 OntologyListener listener,
                                 FileValidator fileValidator,
                                 OntologyMethodsSummarizer summarizer,
                                 Path path,
                                 Map<String, BillableTotals> billableTotals) throws Exception {
        final String language = languageFromExtension(path);
        final String relPath = repoRoot.relativize(path).toString();

        boolean eligible = fileValidator.isEligibleForLLMProcessing(
                solution.getName(), component.getName(), relPath, path.toFile());
        if (!eligible) {
            logger.debug("Skipping (already summarized and unchanged): {}", relPath);
            return;
        }

        logger.info("Summarizing [{}] {} :: {}", language, component.getName(), relPath);
        final AiEnrichmentPayload payload = summarizer.summarizeCodeMethods(relPath, language, path.toFile());
        if (payload == null) {
            logger.warn("No enrichment returned for: {}", relPath);
            return;
        }
        payload.setComponent(component.getName());
        payload.setSolution(solution.getName());
        logBillableUsage(relPath, payload, billableTotals);

        if (payload.getFunctionEnrichments() == null || payload.getFunctionEnrichments().isEmpty()) {
            logger.warn("No enrichment returned for: {}", relPath);
            return;
        }
        listener.processLLMEnrichment(solution, component, relPath, payload);
        fileValidator.aiParseCompleted(solution.getName(), component.getName(), relPath, path.toFile());
        logger.info("AI summarization recorded for: {}", relPath);
    }

    private void processTemplateFile(Path repoRoot,
                                     Solution solution,
                                     Component component,
                                     OntologyListener listener,
                                     FileValidator fileValidator,
                                     OntologyMethodsSummarizer summarizer,
                                     Path path,
                                     Map<Path, String> assetCache,
                                     Map<String, BillableTotals> billableTotals) throws Exception {
        final String relPath = repoRoot.relativize(path).toString();
        boolean eligible = fileValidator.isEligibleForLLMProcessing(
                solution.getName(), component.getName(), relPath, path.toFile());
        if (!eligible) {
            logger.debug("Skipping (already summarized and unchanged): {}", relPath);
            return;
        }

        final String language = templateLanguageFromExtension(path);
        final String templateContent = Files.readString(path, StandardCharsets.UTF_8);
        TemplateBundle bundle = buildTemplateBundle(repoRoot, path, templateContent, component.getName(), assetCache);

        AiEnrichmentPayload payload = summarizer.summarizeGuiTemplate(relPath, language, bundle.bundleText);
        if (payload == null) {
            logger.warn("No enrichment returned for: {}", relPath);
            return;
        }
        payload.setComponent(component.getName());
        payload.setSolution(solution.getName());
        if (!bundle.relationships.isEmpty()) {
            payload.setFileRelationships(bundle.relationships);
        }
        logBillableUsage(relPath, payload, billableTotals);

        listener.processLLMEnrichment(solution, component, relPath, payload);
        fileValidator.aiParseCompleted(solution.getName(), component.getName(), relPath, path.toFile());
        logger.info("AI template summarization recorded for: {}", relPath);
    }

    private void processConfigFile(Path repoRoot,
                                   Solution solution,
                                   Component component,
                                   OntologyListener listener,
                                   FileValidator fileValidator,
                                   OntologyMethodsSummarizer summarizer,
                                   Path path,
                                   String fileName,
                                   Map<String, BillableTotals> billableTotals) throws Exception {
        final String relPath = repoRoot.relativize(path).toString();
        boolean eligible = fileValidator.isEligibleForLLMProcessing(
                solution.getName(), component.getName(), relPath, path.toFile());
        if (!eligible) {
            logger.debug("Skipping (already summarized and unchanged): {}", relPath);
            return;
        }

        final String content = Files.readString(path, StandardCharsets.UTF_8);
        final String detectedType = detectConfigType(path, fileName);
        AiEnrichmentPayload payload = summarizer.summarizeConfigTemplate(relPath, detectedType, content);
        if (payload == null) {
            logger.warn("No enrichment returned for: {}", relPath);
            return;
        }
        payload.setComponent(component.getName());
        payload.setSolution(solution.getName());
        logBillableUsage(relPath, payload, billableTotals);
        listener.processLLMEnrichment(solution, component, relPath, payload);
        fileValidator.aiParseCompleted(solution.getName(), component.getName(), relPath, path.toFile());
        logger.info("AI config summarization recorded for: {}", relPath);
    }

    private void processDocumentFile(Path repoRoot,
                                     Solution solution,
                                     Component component,
                                     OntologyListener listener,
                                     FileValidator fileValidator,
                                     OntologyMethodsSummarizer summarizer,
                                     Path path,
                                     Map<String, BillableTotals> billableTotals) throws Exception {
        final String relPath = repoRoot.relativize(path).toString();
        boolean eligible = fileValidator.isEligibleForLLMProcessing(
                solution.getName(), component.getName(), relPath, path.toFile());
        if (!eligible) {
            logger.debug("Skipping (already summarized and unchanged): {}", relPath);
            return;
        }

        DocumentExtractor.DocumentExtraction extracted = DocumentExtractor.extract(path);
        AiEnrichmentPayload payload = summarizer.summarizeDocument(
                relPath,
                extracted.docType(),
                extracted.title(),
                extracted.datetime(),
                extracted.extractedText()
        );
        if (payload == null) {
            logger.warn("No enrichment returned for: {}", relPath);
            return;
        }
        payload.setComponent(component.getName());
        payload.setSolution(solution.getName());
        logBillableUsage(relPath, payload, billableTotals);
        listener.processLLMEnrichment(solution, component, relPath, payload);
        fileValidator.aiParseCompleted(solution.getName(), component.getName(), relPath, path.toFile());
        logger.info("AI document summarization recorded for: {}", relPath);
    }

    private static void logBillableUsage(String relPath,
                                         AiEnrichmentPayload payload,
                                         Map<String, BillableTotals> billableTotals) {
        if (payload == null || payload.getBillableUsage() == null) {
            return;
        }
        AiEnrichmentPayload.BillableUsage usage = payload.getBillableUsage();
        if (usage.getFilePath() == null || usage.getFilePath().isBlank()) {
            usage.setFilePath(relPath);
        }
        String model = usage.getModel() != null && !usage.getModel().isBlank()
                ? usage.getModel()
                : "unknown";

        logger.info(
                "Billable usage [{}] {} -> input={}, output={}, cached={}, total={}",
                model,
                usage.getFilePath(),
                usage.getInputTokens(),
                usage.getOutputTokens(),
                usage.getCachedTokens(),
                usage.getTotalTokens()
        );

        BillableTotals totals = billableTotals.computeIfAbsent(model, k -> new BillableTotals());
        totals.add(usage);
    }

    private static class BillableTotals {
        private final LongAdder files = new LongAdder();
        private final LongAdder inputTokens = new LongAdder();
        private final LongAdder outputTokens = new LongAdder();
        private final LongAdder cachedTokens = new LongAdder();
        private final LongAdder totalTokens = new LongAdder();

        private void add(AiEnrichmentPayload.BillableUsage usage) {
            files.increment();
            inputTokens.add(usage.getInputTokens());
            outputTokens.add(usage.getOutputTokens());
            cachedTokens.add(usage.getCachedTokens());
            totalTokens.add(usage.getTotalTokens());
        }
    }

    private String detectConfigType(Path path, String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.equals("requirements.txt") || lower.endsWith("-requirements.txt")) {
            return "requirements";
        }
        if (lower.endsWith(".env") || lower.startsWith(".env.")) {
            return "env";
        }
        return configTypeFromExtension(path);
    }

    private TemplateBundle buildTemplateBundle(Path repoRoot,
                                               Path templatePath,
                                               String templateContent,
                                               String componentName,
                                               Map<Path, String> assetCache) {
        String relPath = repoRoot.relativize(templatePath).toString();
        StringBuilder bundle = new StringBuilder();
        bundle.append("=== TEMPLATE: ").append(relPath).append(" ===\n");
        bundle.append(templateContent).append("\n");

        List<AiEnrichmentPayload.Edge> relationships = new ArrayList<>();
        List<String> assetRefs = extractAssetReferences(templateContent);

        for (String rawRef : assetRefs) {
            String cleanedRef = sanitizeAssetRef(rawRef);
            if (cleanedRef.isEmpty() || looksDynamic(cleanedRef)) {
                continue;
            }

            Path resolved = resolveAssetPath(repoRoot, templatePath, cleanedRef);
            if (resolved == null) {
                continue;
            }
            if (shouldSkipPath(resolved, false, ingestionRules)) {
                continue;
            }
            if (isTooLarge(resolved, ingestionRules)) {
                relationships.add(buildRelationship(componentName, relPath, cleanedRef,
                        "TEMPLATE_ASSET_SKIPPED",
                        "Referenced asset skipped due to size limit"));
                continue;
            }

            if (!Files.exists(resolved)) {
                relationships.add(buildRelationship(componentName, relPath, cleanedRef,
                        "TEMPLATE_MISSING_ASSET",
                        "Referenced asset not found: " + rawRef));
                continue;
            }

            if (!isStaticAsset(resolved, ingestionRules)) {
                relationships.add(buildRelationship(componentName, relPath, cleanedRef,
                        "TEMPLATE_REFERENCES_ASSET",
                        "Referenced asset: " + rawRef));
                continue;
            }

            String assetContent = assetCache.computeIfAbsent(resolved, p -> {
                try {
                    return Files.readString(p, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return null;
                }
            });
            if (assetContent == null) {
                relationships.add(buildRelationship(componentName, relPath, cleanedRef,
                        "TEMPLATE_ASSET_READ_ERROR",
                        "Referenced asset could not be read: " + rawRef));
                continue;
            }

            bundle.append("\n=== ASSET: ").append(repoRoot.relativize(resolved)).append(" ===\n");
            bundle.append(assetContent).append("\n");
            relationships.add(buildRelationship(componentName, relPath, cleanedRef,
                    "TEMPLATE_REFERENCES_ASSET",
                    "Referenced asset: " + rawRef));
        }

        return new TemplateBundle(bundle.toString(), relationships);
    }

    private static AiEnrichmentPayload.Edge buildRelationship(String componentName,
                                                              String templateRelPath,
                                                              String assetRelPath,
                                                              String type,
                                                              String description) {
        AiEnrichmentPayload.Edge edge = new AiEnrichmentPayload.Edge();
        edge.setType(type);
        edge.setSource(componentName + "@template:" + templateRelPath);
        edge.setTarget("asset:" + assetRelPath);
        edge.setDescription(description);
        return edge;
    }

    private static Path resolveAssetPath(Path repoRoot, Path templatePath, String assetRef) {
        Path candidate;
        if (assetRef.startsWith("/")) {
            candidate = repoRoot.resolve(assetRef.substring(1));
        } else {
            Path parent = templatePath.getParent();
            if (parent == null) {
                candidate = repoRoot.resolve(assetRef);
            } else {
                candidate = parent.resolve(assetRef);
            }
        }
        Path normalized = candidate.normalize();
        if (!normalized.startsWith(repoRoot)) {
            return null;
        }
        return normalized;
    }

    private static List<String> extractAssetReferences(String content) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> results = new ArrayList<>();
        Matcher scripts = SCRIPT_SRC.matcher(content);
        while (scripts.find()) {
            results.add(scripts.group(1));
        }
        Matcher links = LINK_TAG.matcher(content);
        while (links.find()) {
            String tag = links.group(0);
            if (tag.toLowerCase().contains("rel=\"stylesheet\"") || tag.toLowerCase().contains("rel='stylesheet'")) {
                String href = extractAttribute(tag, "href");
                if (href != null) {
                    results.add(href);
                }
            }
        }
        return results;
    }

    private static String extractAttribute(String tag, String attr) {
        Pattern p = Pattern.compile(attr + "\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(tag);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String sanitizeAssetRef(String ref) {
        if (ref == null) {
            return "";
        }
        String cleaned = ref.trim();
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://") || cleaned.startsWith("//")) {
            return "";
        }
        int split = cleaned.indexOf('?');
        if (split >= 0) {
            cleaned = cleaned.substring(0, split);
        }
        split = cleaned.indexOf('#');
        if (split >= 0) {
            cleaned = cleaned.substring(0, split);
        }
        return cleaned.trim();
    }

    private static boolean looksDynamic(String ref) {
        return ref.contains("{{") || ref.contains("}}") || ref.contains("${") || ref.contains("<%") || ref.contains("%>");
    }

    private static boolean isStaticAsset(Path path, IngestionRules rules) {
        String ext = extensionOf(path.getFileName().toString());
        return rules.staticAssetExtensions.contains(ext);
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new RuntimeException("Unable to compute SHA-256", e);
        }
    }

    /**
     * Build the summarizer implementation once per component run.
     * Extend with more providers as you add them.
     */
    private static OntologyMethodsSummarizer buildSummarizer(String provider) {
        Objects.requireNonNull(provider, "provider");
        switch (provider.toLowerCase()) {
            case "deepseek":
                // Assuming your DeepSeek implementation lives here:
                // org.manishsharan.graphbuilder.job.job.deepseek.DeepSeekSummarizer
                return  DeepSeekSummarizer.getInstance();
            case "gemini":
                 return  org.manishsharan.madladlabs.genai.summarizers.ai.gemini.GeminiSummarizer.getInstance();
            case "haiku":
                 return org.manishsharan.madladlabs.genai.summarizers.ai.anthropic.HaikuSummarizer.getInstance();
            default:
                throw new IllegalArgumentException("Unknown summarizer provider: " + provider);
        }
    }

    private static final Pattern SCRIPT_SRC = Pattern.compile("<script[^>]*\\ssrc\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_TAG = Pattern.compile("<link[^>]*>", Pattern.CASE_INSENSITIVE);

    private static final class TemplateBundle {
        private final String bundleText;
        private final List<AiEnrichmentPayload.Edge> relationships;

        private TemplateBundle(String bundleText, List<AiEnrichmentPayload.Edge> relationships) {
            this.bundleText = bundleText;
            this.relationships = relationships;
        }
    }

    static final class IngestionRules {
        final Set<String> codeExtensions;
        final Set<String> templateExtensions;
        final Set<String> staticAssetExtensions;
        final Set<String> configExtensions;
        final Set<String> documentExtensions;
        final Set<String> skipDirectoryNames;
        final Set<String> skipFileNames;
        final Set<String> skipFileExtensions;
        final Set<String> allowHiddenDirectories;
        final Set<String> configFileNames;
        final boolean skipHidden;
        final boolean skipMinified;
        final long maxFileSizeBytes;

        private IngestionRules(Set<String> codeExtensions,
                               Set<String> templateExtensions,
                               Set<String> staticAssetExtensions,
                               Set<String> configExtensions,
                               Set<String> documentExtensions,
                               Set<String> skipDirectoryNames,
                               Set<String> skipFileNames,
                               Set<String> skipFileExtensions,
                               Set<String> allowHiddenDirectories,
                               Set<String> configFileNames,
                               boolean skipHidden,
                               boolean skipMinified,
                               long maxFileSizeBytes) {
            this.codeExtensions = codeExtensions;
            this.templateExtensions = templateExtensions;
            this.staticAssetExtensions = staticAssetExtensions;
            this.configExtensions = configExtensions;
            this.documentExtensions = documentExtensions;
            this.skipDirectoryNames = skipDirectoryNames;
            this.skipFileNames = skipFileNames;
            this.skipFileExtensions = skipFileExtensions;
            this.allowHiddenDirectories = allowHiddenDirectories;
            this.configFileNames = configFileNames;
            this.skipHidden = skipHidden;
            this.skipMinified = skipMinified;
            this.maxFileSizeBytes = maxFileSizeBytes;
        }

        static IngestionRules fromConfig(FileIngestionConfig config) {
            FileIngestionConfig defaults = defaultConfig();
            FileIngestionConfig cfg = config != null ? config : defaults;
            return new IngestionRules(
                    toLowerSet(cfg.getCodeExtensions() != null ? cfg.getCodeExtensions() : defaults.getCodeExtensions()),
                    toLowerSet(cfg.getTemplateExtensions() != null ? cfg.getTemplateExtensions() : defaults.getTemplateExtensions()),
                    toLowerSet(cfg.getStaticAssetExtensions() != null ? cfg.getStaticAssetExtensions() : defaults.getStaticAssetExtensions()),
                    toLowerSet(cfg.getConfigExtensions() != null ? cfg.getConfigExtensions() : defaults.getConfigExtensions()),
                    toLowerSet(cfg.getDocumentExtensions() != null ? cfg.getDocumentExtensions() : defaults.getDocumentExtensions()),
                    toSet(cfg.getSkipDirectoryNames() != null ? cfg.getSkipDirectoryNames() : defaults.getSkipDirectoryNames()),
                    toSet(cfg.getSkipFileNames() != null ? cfg.getSkipFileNames() : defaults.getSkipFileNames()),
                    toLowerSet(cfg.getSkipFileExtensions() != null ? cfg.getSkipFileExtensions() : defaults.getSkipFileExtensions()),
                    toSet(cfg.getAllowHiddenDirectories() != null ? cfg.getAllowHiddenDirectories() : defaults.getAllowHiddenDirectories()),
                    toSet(cfg.getConfigFileNames() != null ? cfg.getConfigFileNames() : defaults.getConfigFileNames()),
                    cfg.isSkipHidden(),
                    cfg.isSkipMinified(),
                    cfg.getMaxFileSizeBytes() > 0 ? cfg.getMaxFileSizeBytes() : defaults.getMaxFileSizeBytes()
            );
        }

        private static FileIngestionConfig defaultConfig() {
            FileIngestionConfig cfg = new FileIngestionConfig();
            cfg.setCodeExtensions(List.of(".java", ".py", ".clj"));
            cfg.setTemplateExtensions(List.of(
                    ".html", ".htm", ".jinja", ".j2", ".jinja2", ".twig", ".hbs", ".handlebars",
                    ".mustache", ".ejs", ".erb", ".jsp", ".jspx", ".ftl", ".vm", ".thymeleaf",
                    ".liquid", ".jrxml", ".rdl", ".rpt"
            ));
            cfg.setStaticAssetExtensions(List.of(".js", ".mjs", ".cjs", ".ts", ".tsx", ".css"));
            cfg.setConfigExtensions(List.of(".env", ".properties", ".conf", ".cfg", ".ini", ".toml",
                    ".yaml", ".yml", ".json", ".xml"));
            cfg.setDocumentExtensions(List.of(
                    ".doc", ".docx", ".rtf", ".rtx", ".txt", ".md", ".pdf", ".ppt", ".pptx",
                    ".xls", ".xlsx"
            ));
            cfg.setConfigFileNames(List.of(
                    "application.properties", "application.yml", "application.yaml", "bootstrap.yml",
                    "bootstrap.yaml", "log4j.properties", "log4j2.xml", "logback.xml", "pom.xml",
                    "settings.xml", "gradle.properties", "build.gradle", "build.gradle.kts",
                    "settings.gradle", "settings.gradle.kts", "hibernate.cfg.xml", "persistence.xml",
                    "liquibase.properties", "liquibase.yaml", "flyway.conf", "package.json",
                    "package-lock.json", "pnpm-lock.yaml", "yarn.lock", ".npmrc", ".yarnrc",
                    ".yarnrc.yml", "tsconfig.json", "jsconfig.json", "babel.config.json", ".babelrc",
                    "webpack.config.js", "vite.config.js", "eslint.config.js", ".eslintrc",
                    ".prettierrc", ".prettierrc.json", "requirements.txt", "requirements-dev.txt",
                    "constraints.txt", "pyproject.toml", "setup.cfg", "setup.py", "pip.conf",
                    "tox.ini", "pytest.ini", ".flake8", "poetry.lock", "go.mod", "go.sum",
                    ".golangci.yml", "Cargo.toml"
            ));
            cfg.setSkipDirectoryNames(List.of(
                    "node_modules", "bower_components", "vendor", "dist", "build", "out", "target",
                    "bin", "obj", ".gradle", ".idea", ".vscode", ".settings", ".metadata",
                    ".terraform", ".serverless", ".next", ".nuxt", "coverage", ".cache",
                    ".parcel-cache", ".pytest_cache", "__pycache__", ".mypy_cache", ".ruff_cache",
                    ".tox", ".venv", "venv", "env", "CMakeFiles", "cmake-build-debug",
                    "cmake-build-release", ".vs", ".svn"
            ));
            cfg.setSkipFileNames(List.of(".classpath", ".project", ".env", ".DS_Store"));
            cfg.setSkipFileExtensions(List.of(
                    ".class", ".jar", ".war", ".ear", ".kotlin_module", ".map",
                    ".pyc", ".pyo", ".o", ".obj", ".a", ".so", ".dll", ".dylib", ".exe"
            ));
            cfg.setAllowHiddenDirectories(List.of(".github"));
            cfg.setSkipHidden(true);
            cfg.setSkipMinified(true);
            cfg.setMaxFileSizeBytes(1_000_000L);
            return cfg;
        }

        private static Set<String> toSet(List<String> values) {
            if (values == null || values.isEmpty()) {
                return new HashSet<>();
            }
            return new HashSet<>(values);
        }

        private static Set<String> toLowerSet(List<String> values) {
            if (values == null || values.isEmpty()) {
                return new HashSet<>();
            }
            Set<String> out = new HashSet<>();
            for (String value : values) {
                if (value != null) {
                    out.add(value.toLowerCase());
                }
            }
            return out;
        }
    }
}
