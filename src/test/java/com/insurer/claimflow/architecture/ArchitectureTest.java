package com.insurer.claimflow.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Guards the hexagonal structure: the domain is framework-free, the application core never depends on adapters,
 * and adapters do not call each other across modules.
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.insurer.claimflow");
    }

    @Test
    void domainIsFrameworkFree() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "org.hibernate..", "org.apache.kafka..",
                        "com.fasterxml.jackson..", "..adapter..", "..application..")
                .because("domain models must be pure Java")
                .check(classes);
    }

    @Test
    void applicationDoesNotDependOnAdapters() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..")
                .because("the application core talks to the outside world only through ports")
                .check(classes);
    }

    @Test
    void applicationDoesNotUseJpaKafkaOrWeb() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..", "org.hibernate..", "org.apache.kafka..",
                        "org.springframework.kafka..", "org.springframework.web..", "org.springframework.data..")
                .because("persistence, messaging and HTTP technology belong in adapters")
                .check(classes);
    }

    @Test
    void adaptersOfDifferentModulesAreIndependent() {
        slices()
                .matching("com.insurer.claimflow.(*).adapter..")
                .should().notDependOnEachOther()
                .because("modules collaborate through application ports, not through each other's adapters")
                .check(classes);
    }

    @Test
    void portsAreInterfacesOrRecords() {
        classes()
                .that().resideInAPackage("..application.port..")
                .and().areTopLevelClasses()
                .should().beInterfaces()
                .orShould().beRecords()
                .because("ports are contracts")
                .check(classes);
    }
}
