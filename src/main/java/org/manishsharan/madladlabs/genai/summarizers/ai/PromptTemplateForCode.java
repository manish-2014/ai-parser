package org.manishsharan.madladlabs.genai.summarizers.ai;

public class PromptTemplateForCode {

    public static String PROMPT_TEMPLATE = """
You are an expert software analyst. I will provide ONE FULL {{{language}}} SOURCE FILE . 
Extract enrichment ONLY for functions/methods DEFINED in this file (skip imports and external symbols). 

Return a single JSON object with a "functions" array. For each function/method in this file, return: 
- "fqn": fully-qualified name (module.namespace + class + method or module + function; use language-appropriate naming).
- "description": 4–7 sentences MAX in normal/business English describing WHAT it does, WHY it exists, and HOW it works (major steps), including:
    - If it directly HANDLES a Web route (HTTP), SOAP, REST, or GraphQL endpoint.
    - If it directly CALLS/INVOKES a Web route, SOAP, REST, or GraphQL endpoint.
    - If it directly ACCESSES a database (SQL/NoSQL/Redis/etc), including whether it reads or writes, and what entity/table/collection if inferable.
    - If it touches potentially sensitive data (emails, IDs, PII), name it.
  Keep this concise and high-signal for vector embeddings.
- "relationships": ONLY list additional edges about the above API/route/db interactions (omit anything the static parser already provides, like generic CALLS/CONTAINS/DEPENDS_ON).
  Use at most 6 edges per function. Supported types:
    - "ROUTE_HANDLES"  (source="GET /path/:param" or "POST /path" or ANY or PATCH or PATCH or PUT, target=function FQN )
    - "ROUTE_CALLS"    (source=function FQN, target="GET /path" or "POST /path")
    - "SOAP_HANDLES"   (source="Service#Operation", target=function FQN)
    - "SOAP_CALLS"     (source=function FQN, target="Service#Operation")
    - "REST_CALLS"     (source=function FQN, target="METHOD https://host/path{?q}" or templated path)
    - "GRAPHQL_HANDLES"(source="query OperationName" | "mutation OperationName", target=function FQN, )
    - "GRAPHQL_CALLS"  (source=function FQN, target="query OperationName" | "mutation OperationName")
    - "DB_ACCESSES"    (source=function FQN, target="READ table:users" | "WRITE collection:videos" | "READ key:redis:session:{id}")
  If uncertain on exact target, use the best specific string available (e.g., function/var name, route template, table/collection key).

Important rules:
- Do NOT restate facts available from parsing (name, line, visibility, raw call lists).
- Only include functions/methods actually defined in THIS file.
- Prefer precise, templated targets (e.g., "GET /video/load/:site/:id", "table:orders") over vague text.
- Keep output minimal. No prose outside JSON. No code blocks in JSON values. No markdown.

Output JSON schema (exact keys, no extras):
{
  "module": "string (module or namespace or  classname of the source file)",
  "language": "string (programming language of the source file)",
  "functions": [
    {
      "fqn": "string",
      "description": "string (4–7 sentences, business English, includes route/API/db info inline)",
      "relationships": [
        {"type": "ROUTE_HANDLES" | "ROUTE_CALLS" | "SOAP_HANDLES" | "SOAP_CALLS" | "REST_CALLS" | "GRAPHQL_HANDLES" | "GRAPHQL_CALLS" | "DB_ACCESSES",
         "source": "string (function FQN)",
         "target": "string (route/api/db target as specified)"
         "description": "string (optional, brief explanation of the relationship. This would be used for sematic search using embedding vectors) "
         }
      ]
    }
  ]
}

Heuristics (for detection signals, not to be output):
- Web routes: Clojure Compojure/Ring/Liberator (GET/POST/PUT/DELETE/OPTIONS), Django/Flask/FastAPI, Express/Koa, Spring MVC/JAX-RS, Rails routing, etc.
- REST calls: clj-http, http-kit, Java HttpClient/OkHttp, Python requests/httpx, fetch/axios, curl wrappers, etc.
- GraphQL: graphql-request, Apollo, gql tags, graphql-java, Strawberry/FastAPI GraphQL.
- SOAP: javax.xml.ws / Spring-WS, zeep/suds, WSDL clients.
- DB access: JDBC/JPA/Hibernate, MyBatis, psycopg2/sqlalchemy, Mongo/Motor, Redis clients, ORM repositories.

Now I will provide the full source file. Produce ONLY the JSON per the schema above.
This is the relatinve path of the file in the project: {{{relativeFilePath}}}
-----------------------------------------------------------------------------------------------            
         {{{sourceFileContent}}}   
-----------------------------------------------------------------------------------------------

            """;

}
