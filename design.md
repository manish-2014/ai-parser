Design Overview: AI Parser Ingestion

Goals
- Skip generated/vendor/build outputs deterministically during repository traversal.
- Add dedicated ingestion paths for GUI templates and configuration files.
- Add document ingestion for Tika-supported formats (PDF, Office, Markdown, etc.).
- Keep existing code-method summarization intact and compatible.
- Map new GUI/config outputs into the existing ontology/graph relationships.

Architecture Summary
- AIComponentProcessor performs a recursive walk using Files.walkFileTree and applies a centralized skip policy.
- Files are routed to one of three ingestion paths: code, template/GUI, or config.
- A fourth ingestion path extracts document text and metadata via Apache Tika.
- A shared OntologyMethodsSummarizer interface exposes summarizeCodeMethods, summarizeGuiTemplate, summarizeConfigTemplate.
- The interface also exposes summarizeDocument for extracted document content.
- AiEnrichmentPayload now carries template/config summaries and file-level relationships in addition to function enrichments.
- OntologyEnrichmentApplier maps file-level and GUI relationships into the graph, normalizing non-code targets.

Key Components

1) Centralized skip rules
- shouldSkipPath(Path, isDirectory, rules) handles hidden files, build/vendor dirs, and compiled artifacts.
- Rules are configurable through FileIngestionConfig (defaults provided if not set).
- Symlinks are not followed to avoid loops.
- Skipped items are logged at DEBUG level only.

2) Code ingestion flow
- Matches code extensions (default: .java, .py, .clj).
- Uses existing LLM prompt (PromptTemplateForCode).
- Output populates AiEnrichmentPayload.functionEnrichments and related edges.

3) Template/GUI ingestion flow
- Matches template extensions (e.g., .html, .hbs, .jinja, .jsp, .ftl, .jrxml).
- Extracts referenced JS/CSS assets from HTML-like templates.
- Two-pass bundle:
  - pass 1: read template, detect script/link assets
  - pass 2: load referenced assets (if present, non-minified, size-safe) and append to bundle
- Missing assets never crash ingestion. A relationship edge with evidence is emitted instead.
- Uses PromptTemplateForGUITemplates and stores output as AiEnrichmentPayload.templateEnrichments.
- File-level asset links are stored as AiEnrichmentPayload.fileRelationships.

4) Config ingestion flow
- Matches config extensions and known config filenames (pom.xml, package.json, requirements.txt, etc).
- Supports .env and .env.* patterns.
- Uses PromptTemplateForConfigTemplates to produce a verbose summary.
- Output stored as AiEnrichmentPayload.configEnrichments (summary text plus detected type).

5) Document ingestion flow
- Matches document extensions (doc/docx/rtf/md/txt/pdf/ppt/xls).
- Uses Apache Tika to extract text and metadata (title, modified/created time).
- Uses PromptTemplateForDocuments to produce a structured text summary.
- Output stored as AiEnrichmentPayload.documentEnrichments.

5) Size limits
- maxFileSizeBytes is configurable (default 1,000,000 bytes).
- Oversized files are skipped but recorded as a FileNote in the payload.

Data Flow
- AIComponentProcessor -> Summarizer -> AiEnrichmentPayload -> OntologyListener
- OntologyEnrichmentApplier converts payload edges (function and file-level) into RelationshipEdge objects.
- Graph ingestion uses normalized non-code targets to create Annotation nodes when needed.

Configurability
- FileIngestionConfig defines:
  - codeExtensions
  - templateExtensions
  - staticAssetExtensions
  - configExtensions
  - configFileNames
  - skipDirectoryNames
  - skipFileNames
  - skipFileExtensions
  - allowHiddenDirectories (e.g., .github)
  - skipHidden, skipMinified, maxFileSizeBytes
- Defaults are applied when config values are absent.

Determinism and Robustness
- Traversal is deterministic and does not follow symlinks.
- Missing assets and large files do not fail ingestion.
- Skips are silent at INFO level to reduce noise.

Testing
- Unit tests validate skip logic and template selection in AIComponentProcessorTest.
