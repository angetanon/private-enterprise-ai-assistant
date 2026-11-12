package com.oriontech.assistant;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureTest {

    private static final String BASE = "com.oriontech.assistant";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE);
    }

    // ------------------------------------------------------------ safeguard

    @Test
    void classes_are_imported() {
        // avoids a false "green" if the base package name is wrong
        assertThat(classes.size()).isPositive();
    }

    // ---------------------------------------------------------- no cycles

    @Test
    void modules_are_free_of_cycles() {
        slices().matching(BASE + ".(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }

    // --------------------------------------------- dependency direction

    @Test
    void runtime_depends_on_no_other_module() {
        assertModuleDoesNotDependOn("runtime",
                "rag", "knowledge", "assistant", "identity", "agent", "governance");
    }

    @Test
    void rag_does_not_depend_on_knowledge_or_assistant() {
        assertModuleDoesNotDependOn("rag", "knowledge", "assistant");
    }

    @Test
    void knowledge_goes_through_rag_only() {
        assertModuleDoesNotDependOn("knowledge", "runtime", "assistant");
    }

    @Test
    void assistant_does_not_depend_on_rag_or_knowledge() {
        assertModuleDoesNotDependOn("assistant", "rag", "knowledge");
    }

    @Test
    void identity_is_a_leaf_module() {
        assertModuleDoesNotDependOn("identity",
                "runtime", "rag", "knowledge", "assistant", "agent", "governance");
    }

    @Test
    void governance_is_a_leaf_module() {
        assertModuleDoesNotDependOn("governance",
                "runtime", "rag", "knowledge", "assistant", "agent", "identity");
    }

    // ------------------------------------------------------ technical rules

    @Test
    void internal_modules_expose_no_http_endpoint() {
        noClasses().that().resideInAnyPackage(BASE + ".runtime..", BASE + ".rag..")
                .should().beAnnotatedWith(RestController.class)
                .check(classes);
    }

    @Test
    void runtime_does_not_use_spring_web() {
        noClasses().that().resideInAPackage(BASE + ".runtime..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..")
                .check(classes);
    }

    @Test
    void spring_security_is_confined_to_identity() {
        noClasses().that().resideOutsideOfPackage(BASE + ".identity..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.security..")
                .check(classes);
    }

    // ------------------------------------------------------------------ utils

    private static void assertModuleDoesNotDependOn(String module, String... forbiddenModules) {
        String[] forbiddenPackages = Arrays.stream(forbiddenModules)
                .map(m -> BASE + "." + m + "..")
                .toArray(String[]::new);

        noClasses().that().resideInAPackage(BASE + "." + module + "..")
                .should().dependOnClassesThat().resideInAnyPackage(forbiddenPackages)
                .allowEmptyShould(true)      // identity, agent, governance are still empty
                .check(classes);
    }
}