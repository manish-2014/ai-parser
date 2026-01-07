package org.manishsharan.madladlabs.genai.summarizers.ai;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import org.manishsharan.ontology.llmdto.JavaClassSummary;
import org.manishsharan.madladlabs.genai.summarizers.ai.deepseek.DeepSeekSummarizer;
import org.manishsharan.ontology.llmdto.MethodSummary;
//import org.manishsharan.graphbuilder.job.ai.deepseek.DeepSeekSummarizer;
//import org.manishsharan.graphbuilder.job.codeanalyzer.llmdto.JavaClassSummary;
//import org.manishsharan.graphbuilder.job.codeanalyzer.llmdto.MethodSummary;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
public class DeepSeekExtractAssistantContent {
    private static final Logger logger = LogManager.getLogger(DeepSeekExtractAssistantContent.class);
    String response = """
            {
              "id": "f7e7944d-11c4-4769-ad4c-b461b9dd3d04",
              "object": "chat.completion",
              "created": 1738351359,
              "model": "deepseek-chat",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "```json\\n{\\n  \\"response\\": [\\n    {\\n      \\"className\\": \\"JpaVisitRepositoryImpl\\",\\n      \\"fullyQualifiedClassName\\": \\"org.springframework.samples.petclinic.repository.jpa.JpaVisitRepositoryImpl\\",\\n      \\"classSummary\\": \\"This class is a part of a pet clinic application and is responsible for managing the data related to pet visits. It uses JPA (Java Persistence API) to interact with the database. Specifically, it handles saving visit records and retrieving visits associated with a specific pet.\\",\\n      \\"methods\\": [\\n        {\\n          \\"method\\": \\"save\\",\\n          \\"parent\\": \\"JpaVisitRepositoryImpl\\",\\n          \\"methodSummary\\": \\"This method saves a visit record to the database. If the visit is new (i.e., it doesn't have an ID yet), it adds the visit to the database. If the visit already exists (i.e., it has an ID), it updates the existing record.\\"\\n        },\\n        {\\n          \\"method\\": \\"findByPetId\\",\\n          \\"parent\\": \\"JpaVisitRepositoryImpl\\",\\n          \\"methodSummary\\": \\"This method retrieves a list of visits from the database that are associated with a specific pet. It takes the pet's ID as input and returns all the visits related to that pet.\\"\\n        }\\n      ]\\n    },\\n    {\\n      \\"className\\": \\"VisitRepository\\",\\n      \\"fullyQualifiedClassName\\": \\"org.springframework.samples.petclinic.repository.VisitRepository\\",\\n      \\"classSummary\\": \\"This is an interface that defines the methods for managing visit data in the pet clinic application. It serves as a contract for classes that implement it, ensuring they provide functionality for saving visits and finding visits by pet ID.\\",\\n      \\"methods\\": [\\n        {\\n          \\"method\\": \\"save\\",\\n          \\"parent\\": \\"VisitRepository\\",\\n          \\"methodSummary\\": \\"This method is intended to save or update a visit record in the database. Implementing classes must provide the logic to handle new and existing visit records.\\"\\n        },\\n        {\\n          \\"method\\": \\"findByPetId\\",\\n          \\"parent\\": \\"VisitRepository\\",\\n          \\"methodSummary\\": \\"This method is intended to retrieve a list of visits associated with a specific pet. Implementing classes must provide the logic to query the database and return the relevant visits based on the pet's ID.\\"\\n        }\\n      ]\\n    }\\n  ]\\n}\\n```"
                  },
                  "logprobs": null,
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 729,
                "completion_tokens": 479,
                "total_tokens": 1208,
                "prompt_tokens_details": {
                  "cached_tokens": 704
                },
                "prompt_cache_hit_tokens": 704,
                "prompt_cache_miss_tokens": 25
              },
              "system_fingerprint": "fp_3a5770e1b4"
            }
            """;

    @Test
    public void ExtractAssistantContent() throws Exception {
        // Mock dependencies
        try {
            // Parse the response JSON

            JsonNode jsonNode=   DeepSeekSummarizer.extractResponsePayloadJsonNode(response);
            assertNotNull(jsonNode);
            logger.info("JsonNode : " + jsonNode.toString());
            // Extract the assistant content
            List<JavaClassSummary> list= PromptUtils.extractMethodSummaries(jsonNode.path("response"));

            assertNotNull(list);
            assertTrue(list.size() > 0);
           for (JavaClassSummary summary : list) {
                logger.info("JavaClassSummary : " + summary.toString());
                assertNotNull(summary.getClassName());
                assertNotNull(summary.getFullyQualifiedClassName());
                assertNotNull(summary.getMethodSummaries());

                for (MethodSummary methodSummary : summary.getMethodSummaries()) {
                    assertNotNull(methodSummary.getMethodName());

                    assertNotNull(methodSummary.getMethodSummary());


                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
