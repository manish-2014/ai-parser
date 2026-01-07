# AI Parser Ingestion Configuration

This module scans repositories for code, GUI templates, and configuration files. The file selection and skip rules are configurable via `JobConfig.fileIngestionConfig` (see `org.manishsharan.ontology.job.config.FileIngestionConfig`).

## Example (YAML/JSON-style)

```
fileIngestionConfig:
  codeExtensions: [".java", ".py", ".clj"]
  templateExtensions: [".html", ".htm", ".jinja", ".j2", ".hbs", ".jsp", ".ftl", ".jrxml"]
  staticAssetExtensions: [".js", ".mjs", ".cjs", ".ts", ".tsx", ".css"]
  configExtensions: [".properties", ".conf", ".cfg", ".ini", ".toml", ".yaml", ".yml", ".json", ".xml"]
  documentExtensions: [".doc", ".docx", ".rtf", ".md", ".txt", ".pdf", ".ppt", ".pptx", ".xls", ".xlsx"]
  configFileNames:
    - application.yml
    - pom.xml
    - package.json
    - requirements.txt
  skipDirectoryNames:
    - node_modules
    - target
    - build
    - dist
  skipFileNames:
    - .classpath
    - .project
    - .env
  skipFileExtensions:
    - .class
    - .jar
    - .map
    - .pyc
  allowHiddenDirectories: [".github"]
  skipHidden: true
  skipMinified: true
  maxFileSizeBytes: 1000000
```

Notes:
- Hidden files and directories are skipped by default; add exceptions in `allowHiddenDirectories`.
- Minified assets (`*.min.js`, `*.min.css`) are skipped when `skipMinified` is true.
- `maxFileSizeBytes` skips large files and records a short "skipped due to size" note in the AI payload.
