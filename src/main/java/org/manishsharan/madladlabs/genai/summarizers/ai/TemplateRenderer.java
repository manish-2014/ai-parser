package org.manishsharan.madladlabs.genai.summarizers.ai;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

public class TemplateRenderer {


    /**
     * Renders the PROMPT_TEMPLATE with the provided data
     *
     * @param data A map containing the data to be used for rendering the template.
     * Expected keys are "language", "relativeFilePath", and "souceFileContent".
     * @return The rendered string.
     * @throws IOException if an I/O error occurs.
     */
    public static String getRenderedPrompt(Map<String, String> data) throws IOException {
        return renderTemplate(PromptTemplateForCode.PROMPT_TEMPLATE, data);
    }

    public static String renderTemplate(String template, Map<String, String> data) throws IOException {
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache mustache = mf.compile(new StringReader(template), "template");
        StringWriter writer = new StringWriter();
        mustache.execute(writer, data).flush();
        return writer.toString();
    }
}
