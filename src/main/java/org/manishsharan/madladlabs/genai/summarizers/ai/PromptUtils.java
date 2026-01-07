package org.manishsharan.madladlabs.genai.summarizers.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.manishsharan.ontology.llmdto.JavaClassSummary;
import org.manishsharan.ontology.llmdto.MethodSummary;

public class PromptUtils {
    static ObjectMapper mapper = new ObjectMapper();




    public static List<JavaClassSummary> extractMethodSummaries(JsonNode responseNode){
        List<JavaClassSummary> classSummaries = new ArrayList<>();
        if (responseNode.isArray()) {
            for (JsonNode classNode : responseNode) {
                String className = classNode.path("className").asText();
                String fullyQualifiedClassName = classNode.path("fullyQualifiedClassName").asText();
                String classSummary = classNode.path("classSummary").asText();
                JsonNode methodsNode =classNode.path("methods");
                List<MethodSummary> methodSummaries = new ArrayList<>();
                for (JsonNode methodNode : methodsNode) {
                    MethodSummary summary = new MethodSummary();
                    summary.setMethodName(methodNode.path("method").asText());

                    summary.setMethodSummary(methodNode.path("methodSummary").asText());
                    methodSummaries.add(summary);
                }
                JavaClassSummary javaClassSummary = new JavaClassSummary();
                javaClassSummary.setClassName(className);
                javaClassSummary.setFullyQualifiedClassName(fullyQualifiedClassName);
                javaClassSummary.setClassSummary(classSummary);
                javaClassSummary.setMethodSummaries(methodSummaries);
                classSummaries.add(javaClassSummary);

            }

        }
        return classSummaries;
    }


}
