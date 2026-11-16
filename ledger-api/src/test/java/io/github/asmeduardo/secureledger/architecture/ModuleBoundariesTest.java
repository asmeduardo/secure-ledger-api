package io.github.asmeduardo.secureledger.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.math.BigDecimal;
import org.springframework.data.repository.Repository;

/**
 * The modules of the monolith (ADR 0001) talk to each other only through their {@code api} package,
 * inside one local transaction when an invariant needs it. These rules keep that true as the code
 * grows.
 */
@AnalyzeClasses(packages = "io.github.asmeduardo.secureledger", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

    private static final String BASE = "io.github.asmeduardo.secureledger";

    @ArchTest
    static final ArchRule modulesHaveNoCycles =
            slices().matching(BASE + ".(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule ledgerDependsOnNoOtherModule = noClasses()
            .that()
            .resideInAPackage("..secureledger.ledger..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..secureledger.accounts..", "..secureledger.transfers..", "..secureledger.audit..")
            .because("the ledger is the core: other modules use it, never the other way round");

    @ArchTest
    static final ArchRule ledgerIsUsedOnlyThroughItsApi = noClasses()
            .that()
            .resideOutsideOfPackage("..secureledger.ledger..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..secureledger.ledger.domain..", "..secureledger.ledger.application..");

    @ArchTest
    static final ArchRule accountsAreUsedOnlyThroughTheirApi = noClasses()
            .that()
            .resideOutsideOfPackage("..secureledger.accounts..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..secureledger.accounts.domain..",
                    "..secureledger.accounts.application..",
                    "..secureledger.accounts.web..");

    @ArchTest
    static final ArchRule sharedHoldsNoBusinessModuleCode = noClasses()
            .that()
            .resideInAPackage("..secureledger.shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..secureledger.accounts..",
                    "..secureledger.ledger..",
                    "..secureledger.transfers..",
                    "..secureledger.audit..");

    @ArchTest
    static final ArchRule domainDependsOnNoOuterLayer = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..application..", "..web..", "..shared.web..", "..shared.security..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnWeb = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..web..")
            .because("HTTP adapters call the application, never the other way round");

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that()
            .resideInAPackage("..web..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Repository.class)
            .because("controllers go through application services, which own transactions");

    @ArchTest
    static final ArchRule moneyIsNeverBuiltFromBinaryFloatingPoint = noClasses()
            .should()
            .callConstructor(BigDecimal.class, double.class)
            .orShould()
            .callMethod(BigDecimal.class, "valueOf", double.class)
            .because("a double cannot represent most decimal amounts exactly");
}
