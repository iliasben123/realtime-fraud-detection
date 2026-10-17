package com.frauddetection.api.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

/**
 * Garde-fous de l'architecture hexagonale :
 *  - le domaine ne dépend de rien d'autre que lui-même et du JDK ;
 *  - la couche application ne connaît ni Spring ni les adaptateurs ;
 *  - les adaptateurs ne se parlent pas directement.
 */
@AnalyzeClasses(packages = "com.frauddetection.api", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule onion = onionArchitecture()
            .domainModels("..domain.model..", "..domain.exception..")
            .domainServices("..domain.port..")
            .applicationServices("..application..")
            .adapter("web", "..adapter.in.web..")
            .adapter("persistence", "..adapter.out.persistence..")
            .adapter("config", "..config..");      // le câblage Spring est un adaptateur comme un autre

    @ArchTest
    static final ArchRule domain_is_framework_free = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.hibernate..", "com.fasterxml..");

    @ArchTest
    static final ArchRule application_is_framework_free = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.hibernate..");
}
