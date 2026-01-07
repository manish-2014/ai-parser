package org.manishsharan.madladlabs.genai.jobcomponent;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.manishsharan.ontology.job.config.Component;
import org.manishsharan.ontology.job.config.Solution;
import org.manishsharan.ontology.listener.OntologyListener;
import org.manishsharan.ontology.model.ModuleNode;
import org.manishsharan.ontology.model.DefinitionNode;
import org.manishsharan.ontology.model.RelationshipEdge;
import org.manishsharan.ontology.model.MetadataNode;
import org.manishsharan.ontology.model.Ontology;
import org.manishsharan.ontology.service.FileValidator;


public class AIToOntologyMapper {


    private static final Logger logger = LogManager.getLogger(AIToOntologyMapper.class);

    /* ----------  constructor state  ---------- */
    private final Path sourceRoot;
    private final Path libsDir;

    /* ----------  output containers  ---------- */
    private final Map<String, ModuleNode>   modules       = new LinkedHashMap<>();
    private final Map<String, DefinitionNode> definitions = new LinkedHashMap<>();
    private final Map<String, MetadataNode> metadata      = new LinkedHashMap<>();
    private final List<RelationshipEdge>    relationships = new ArrayList<>();
    final Component component;
    final Solution solution ;
    /* ----------  plumbing  ---------- */
    private final ObjectMapper om = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /* Used to make relationship IDs unique when the same edge type repeats */
    private final AtomicInteger relCounter = new AtomicInteger();

    OntologyListener ontologyListener;
    FileValidator fileValidator;
    public AIToOntologyMapper(Solution solution, Component component, OntologyListener _ontologyListener, FileValidator _filevalidator) throws IOException {

        this(solution,component );

        this.ontologyListener = _ontologyListener;
        this.fileValidator = _filevalidator;
        ontologyListener.init(solution,component);
    }
    public AIToOntologyMapper(Solution solution,Component component) throws IOException {
        this.solution = solution;
        this.component = component;

        this.sourceRoot = Paths.get(component.getCodeBasePath()).toAbsolutePath().normalize();
        this.libsDir    = component.getLibs() == null ? null : Paths.get(component.getLibs()).toAbsolutePath().normalize();
        if (component.getCodeBasePath() == null ) {
            throw new IllegalArgumentException("Source root path must not be null or empty");
        }
        if(solution.getName()==null || solution.getName().isEmpty() || component==null || component.getName()==null || component.getName().isEmpty()) {
            throw new IllegalArgumentException("Solution name and component name must not be null or empty");
        }

    }

    void clear() {
        modules.clear();
        definitions.clear();
        metadata.clear();
        relationships.clear();
        relCounter.set(0);
    }

    public void analyze() throws IOException {
        try (Stream<Path> files = Files.walk(sourceRoot)) {

            //files.filter(p -> p.toString().endsWith(".java"))
            files.filter(p -> ( p.toString().toLowerCase().endsWith(".clj")
                            || p.toString().endsWith(".cljc")
                            || p.toString().endsWith(".cljs")
                            || p.toString().endsWith(".java")
                            || p.toString().endsWith(".py")
                            || p.toString().endsWith(".js")
                            || p.toString().endsWith(".ts")
                            || p.toString().endsWith(".go")
                            || p.toString().endsWith(".rs")
                            || p.toString().endsWith(".cpp")
                            || p.toString().endsWith(".c")
                            || p.toString().endsWith(".cs")
                            || p.toString().endsWith(".rb")
                            || p.toString().endsWith(".php")
                            || p.toString().endsWith(".swift")

                    ))
                    .filter(Files::isRegularFile)
                    .filter(p -> { // The second filter with an inline lambda
                        try {
                            return this.fileValidator.newOrChangedFile(solution.getName(), component.getName(), relPath(p), p.toFile());
                        } catch (Exception e) {
                            System.err.println("Error validating file '" + p + "': " + e.getMessage());
                            return false; // If validation fails, filter out this file
                        }
                    })
                    .forEach(p -> {
                        processFileSafe(p);
                        this.fileValidator.upsert(solution.getName(), component.getName(), relPath(p), p.toFile());

                    });

        }
    }
    private String relPath(Path abs) { return sourceRoot.relativize(abs).toString().replace(File.separatorChar, '/'); }

//------------------

    public String toJson() throws JsonProcessingException {
        Ontology out = new Ontology(
                new ArrayList<>(modules.values()),
                new ArrayList<>(definitions.values()),
                new ArrayList<>(metadata.values()),
                relationships
        );
        return om.writeValueAsString(out);
    }

    public void toJsonFile(String targetFile) throws IOException {
        Files.writeString(Paths.get(targetFile), toJson());
    }

    /* -------------------------------------------------- internal helpers */

    public void processFileSafe(Path javaFile) {
        try {
            clear();
            if(ontologyListener!= null) {
                ontologyListener.start(relPath(javaFile));
            }

            processFile(javaFile);
            Ontology out = new Ontology(
                    new ArrayList<>(modules.values()),
                    new ArrayList<>(definitions.values()),
                    new ArrayList<>(metadata.values()),
                    relationships
            );
            if(ontologyListener!= null) {
                ontologyListener.completed(relPath(javaFile));
                ontologyListener.processExtractedOntology( solution, component, relPath(javaFile), out );
            }
        }
        catch (Exception ex) { logger.error("Error parsing {}", javaFile, ex); }
    }

    private void processFile(Path javaFile) throws IOException {


    }

//------------------
}
