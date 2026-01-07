package org.manishsharan.madladlabs.genai.summarizers.ai;

public class PromptTemplateForGUITemplates {

    public static String PROMPT_TEMPLATE = """
You are an expert software analyst with expertise in GUI development and template. I will provide ONE FULL {{{language}}} SOURCE FILE . 


You are analyzing a web GUI artifact (template + any linked JS/CSS shown in the input).
Your job is to extract ALL relevant behavioral/contract details so an AI agent can answer questions about the page and make safe, correct edits.

IMPORTANT CONSTRAINTS
- Do NOT output a large nested schema.
- Use the checklist below to ensure coverage, but output only a small JSON payload at the end.
- Everything except the small JSON must be written as VERBOSE TEXT.
- If something is unknown, say “Unknown / not present in provided input” rather than guessing.
- Prefer concrete evidence: quote short snippets (<= 1–2 lines) or name exact ids/classes/urls when present.

CHECKLIST (use this to guide your analysis)
File classification
- file.path (if known), file.type (template/script/style), file.language (html/handlebars/jinja/jsp/jrxml/js/ts/css/unknown)
Template rendering & purpose
- What this page/view is for, where it appears in the app, and how it is rendered (server-side, client-side, layout/partial usage)
Template engine & syntax
- Identify engine (jinja/j2/twig/hbs/mustache/ejs/erb/jsp/jrxml/etc) and the placeholder syntax used
Inputs / placeholders
- List placeholders/variables the template expects, what they represent, whether required/optional, default values if visible
Includes / partials / layouts
- Any referenced includes/partials/fragments/layouts, and what they contribute
Assets
- Scripts/styles used (external vs inline), module/defer hints, and what they control
DOM contract
- Key ids/classes/data-* attributes that are “contractual” (used for styling, JS hooks, tests, or templating)
- Forms: action/method, fields, validations/required, mapping to placeholders/model fields
- Tables/lists: how rows are generated, what data is rendered, empty/loading states
Client-side behavior
- JS bindings: selectors used, what elements they target, and why
- Events: triggers (click/submit/change/load), handlers, side effects (DOM updates, navigation, requests)
Networking / endpoints
- All endpoints used (form post targets, fetch/XHR/htmx, etc):
  method, url, purpose, request fields, response fields, and how response affects the DOM
Data flow & state
- Describe how data moves: placeholder → DOM → request → response → DOM, or globals/local storage if any
Relationships
- Relationships to other templates/scripts/styles (e.g., “this template requires X.js and extends base layout Y”)

REFLECTION STEP (MANDATORY)
Before producing the final JSON, do a quick self-check:
- Did you cover: rendering, placeholders, includes, assets, DOM contract, events, endpoints, data flow?
- If any category is missing, add it to the VERBOSE SUMMARY as “Not present / Unknown”.

OUTPUT FORMAT (STRICT)
1) Write a section titled “VERBOSE SUMMARY” with the full narrative description.
2) Then output ONLY this minimal JSON object (and nothing else after it):

{
  "file": {
    "path": "<string or empty if unknown>",
    "type": "template|script|style",
    "language": "html|handlebars|jinja|jsp|jrxml|js|ts|css|unknown"
  },
  "endpoints": [
    { "method": "", "url": "", "purpose": "", "request_fields": [], "response_fields": [] }
  ],
  "summary": "<A detailed but single string capturing rendering flow, events, triggers, actions, behavior, and what is rendered. Mention key ids/classes/data-* and any includes/assets.>"
}


Now I will provide the full source file. Produce ONLY the JSON per the schema above.
This is the relative path of the file in the project: {{{relativeFilePath}}}
-----------------------------------------------------------------------------------------------            
         {{{sourceFileContent}}}   
-----------------------------------------------------------------------------------------------

""";
}
