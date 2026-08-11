package io.github.joke.percolate.architecture;

import com.netflix.nebula.archrules.core.ArchRulesService;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import java.util.Map;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static io.github.joke.percolate.architecture.Packages.ROOT;

/** Rules confining compiler-service types and the relocated codegen library to their boundaries. */
public class TypeBoundaryRules implements ArchRulesService {

    static final ArchRule STAGE_IMPLEMENTATIONS_ARE_NAMED_STAGE = classes()
            .that()
            .implement(ROOT + ".processor.internal.stages.Stage")
            .should()
            .haveSimpleNameEndingWith("Stage")
            .allowEmptyShould(true)
            .as("Every Stage implementation is named with a Stage suffix")
            .because("a pipeline step is recognisable by name, not only by the interface it implements");

    /**
     * The type-query seam (change type-query-seam): {@code javax.lang.model.util} (Types/Elements) — the two
     * compiler-service classes that need a live compile environment to answer — are confined to the seam
     * impl plus its DI wiring, the discovery adapter, codegen emission, and the nullability resolver. Every
     * other engine/strategy class asks its type questions through the ResolveCtx seam instead, so TypeMirror
     * stays an opaque pass-through token everywhere else.
     *
     * <p>The bare {@code processor} package holds the Dagger wiring (ProcessorModule, MapperStep) plus its
     * generated {@code *_Factory}/{@code DaggerProcessorComponent} siblings, which necessarily mention
     * Types/Elements too.
     */
    static final List<String> TYPE_BOUNDARY_PACKAGES = List.of(
            ROOT + ".processor",
            ROOT + ".processor.internal.stages.expand",
            ROOT + ".processor.internal.stages.discover",
            ROOT + ".processor.internal.stages.generate",
            ROOT + ".processor.nullability");

    /** ResolveCtx declares types()/elements() so a real-javac implementation can answer through them. */
    static final String RESOLVE_CTX = ROOT + ".spi.ResolveCtx";

    static final DescribedPredicate<JavaClass> NOT_TYPE_BOUNDARY = describe(
            "not the seam impl, discovery adapter, codegen emission, or nullability resolver",
            javaClass -> !RESOLVE_CTX.equals(javaClass.getName())
                    && TYPE_BOUNDARY_PACKAGES.stream()
                            .noneMatch(boundary -> javaClass.getPackageName().equals(boundary)
                                    || javaClass.getPackageName().startsWith(boundary + ".")));

    static final ArchRule COMPILER_SERVICES_ARE_CONFINED = noClasses()
            .that(NOT_TYPE_BOUNDARY)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("javax.lang.model.util..")
            .allowEmptyShould(true)
            .as("javax.lang.model.util (Types/Elements) is confined to the type-boundary packages")
            .because("every other class asks its type questions through the ResolveCtx seam, which keeps "
                    + "TypeMirror an opaque pass-through token");

    /**
     * Change relocate-javapoet-as-spi-api: the atomic cutover (design D5) means no production class may ever
     * import the unrelocated upstream package again — a partial regression back to
     * {@code com.palantir.javapoet} would silently reintroduce the processorpath version-clash risk the
     * relocation exists to eliminate. The vendored overlay in {@code lib:javapoet} is authored in that
     * package on purpose and is excluded by that module not applying the runner.
     */
    static final ArchRule NO_UPSTREAM_JAVAPOET = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.palantir.javapoet..")
            .allowEmptyShould(true)
            .as("No production class imports the unrelocated upstream JavaPoet package")
            .because("a foreign JavaPoet version on a shared processorpath would otherwise be substitutable");

    @Override
    public Map<String, ArchRule> getRules() {
        return Map.of(
                "stage-naming", STAGE_IMPLEMENTATIONS_ARE_NAMED_STAGE,
                "compiler-services-confined", COMPILER_SERVICES_ARE_CONFINED,
                "no-upstream-javapoet", NO_UPSTREAM_JAVAPOET);
    }
}
