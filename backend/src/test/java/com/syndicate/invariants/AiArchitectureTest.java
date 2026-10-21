package com.syndicate.invariants;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.syndicate", importOptions = ImportOption.DoNotIncludeTests.class)
public class AiArchitectureTest {

    @ArchTest
    public static final ArchRule aiPackageMustNeverDependOnAuthoritativeRepositories =
            noClasses().that().resideInAPackage("com.syndicate.ai..")
                    .should().dependOnClassesThat()
                    .haveSimpleNameEndingWith("FactRepository")
                    .orShould().dependOnClassesThat().haveSimpleNameEndingWith("ClaimRepository")
                    .orShould().dependOnClassesThat().haveSimpleNameEndingWith("ApprovalRepository")
                    .orShould().dependOnClassesThat().haveSimpleNameEndingWith("FilingApprovalRepository")
                    .orShould().dependOnClassesThat().haveSimpleNameEndingWith("ReviewRepository")
                    .orShould().dependOnClassesThat().haveSimpleNameEndingWith("ConflictRepository")
                    .orShould().dependOnClassesThat().haveSimpleNameEndingWith("AuditEventRepository")
                    .because("Zero AI Authority Invariant 1: AI components must never directly mutate authoritative transaction state.");
}
