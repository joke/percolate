package io.github.joke.percolate.architecture;

import com.netflix.nebula.archrules.core.ArchRulesService;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvent;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * The four method-shape rules, which share one purpose: every authored method must be interceptable by a
 * test double, so it can be tested on its own rather than only through whatever calls it.
 *
 * <p>Exemptions are matched as <em>shapes</em>, never as name lists, so they cannot quietly accumulate
 * members.
 */
public class MethodShapeRules implements ArchRulesService {

    /**
     * Tuned against the decomposed classes: BuildMethodBodies.Walk (13 non-synthetic methods) is the largest
     * legitimate cohesive unit today, so the ceiling clears it with headroom while still catching a
     * regression back toward the pre-decomposition sizes (ExpandStage.Driver was 21 private methods).
     */
    static final int MAX_METHODS_PER_CLASS = 15;

    /** Groovy emits these as ordinary members, not synthetic ones — generated, not authored. */
    static final List<String> GROOVY_METACLASS_ACCESSORS = List.of("getMetaClass", "setMetaClass");

    static final List<String> ENUM_GENERATED_METHODS = List.of("values", "valueOf");

    /** Lambda/{@code access$} bridges and Groovy's synthetic accessors are compiler artifacts. */
    static final DescribedPredicate<JavaMethod> NOT_SYNTHETIC_OR_BRIDGE = DescribedPredicate.describe(
            "not a synthetic or bridge method",
            method -> !method.getModifiers().contains(JavaModifier.SYNTHETIC)
                    && !method.getModifiers().contains(JavaModifier.BRIDGE));

    static final DescribedPredicate<JavaMethod> NOT_GENERATED = DescribedPredicate.describe(
            "not a generated method",
            method -> !isGeneratedOrNestedInGenerated(method.getOwner())
                    && !method.isAnnotatedWith(Packages.LOMBOK_GENERATED));

    /**
     * The published spi surface. {@code strategies-builtin} also lives under the spi package root
     * ({@code io.github.joke.percolate.spi.builtins}) but it is an internal module, not the published
     * contract.
     */
    static final DescribedPredicate<JavaClass> PUBLISHED_SPI =
            DescribedPredicate.describe("on the published spi surface", MethodShapeRules::isPublishedSpi);

    // ---------------------------------------------------------------------------------------------
    // Rule A — no private methods
    // ---------------------------------------------------------------------------------------------

    /**
     * A private method is statically dispatched ({@code invokespecial}) and cannot be intercepted by any
     * test double, so it is not individually testable. Private constructors are automatically exempt —
     * {@code methods()} never matches a constructor. Shaded third-party sources need no filter here: they
     * live in {@code lib:javapoet}, which does not apply the runner, and are never part of another module's
     * own source-set output.
     */
    static final ArchRule NO_PRIVATE_METHODS = methods()
            .that(NOT_SYNTHETIC_OR_BRIDGE)
            .and(DescribedPredicate.describe(
                    "not declared in a @Generated class",
                    (JavaMethod method) -> !isGeneratedOrNestedInGenerated(method.getOwner())))
            .should()
            .notBePrivate()
            .allowEmptyShould(true)
            .as("No method anywhere in percolate is private")
            .because("a private method cannot be intercepted by a test double, so it is not individually "
                    + "testable — prefer a package-private or protected instance method");

    // ---------------------------------------------------------------------------------------------
    // Rule B — size ceiling
    // ---------------------------------------------------------------------------------------------

    /**
     * Co-enforced with Rule A: on its own, Rule A is satisfied by exposing a monolith's guts as
     * package-private members, so this ceiling forces separable logic into a new small collaborator instead
     * of a bigger exposed one.
     */
    static final ArchRule DECOMPOSED_CLASSES_STAY_WITHIN_CEILING = classes()
            .that()
            .resideInAnyPackage(Packages.DECOMPOSED_ENGINE_PACKAGES)
            .should(new ArchCondition<>("declare at most " + MAX_METHODS_PER_CLASS + " non-synthetic methods") {
                @Override
                public void check(final JavaClass javaClass, final ConditionEvents events) {
                    final long count = javaClass.getMethods().stream()
                            .filter(m -> !m.getModifiers().contains(JavaModifier.SYNTHETIC))
                            .count();
                    final String message = javaClass.getSimpleName() + " declares " + count
                            + " non-synthetic methods (ceiling " + MAX_METHODS_PER_CLASS + ")";
                    events.add(
                            count <= MAX_METHODS_PER_CLASS
                                    ? SimpleConditionEvent.satisfied(javaClass, message)
                                    : SimpleConditionEvent.violated(javaClass, message));
                }
            })
            .allowEmptyShould(true)
            .as("No class in the decomposed engine packages exceeds the method-count ceiling")
            .because("Rule A alone is satisfied by exposing a monolith's guts as package-private members");

    // ---------------------------------------------------------------------------------------------
    // Rule C — unused protected methods are marked @VisibleForTesting
    // ---------------------------------------------------------------------------------------------

    /**
     * A concrete protected method is a genuine extension point when a production subclass either overrides
     * it or calls it. Anything else must carry {@code @VisibleForTesting} to document "test-only widening,
     * not a real extension point" as a build-checked fact.
     *
     * <p>The published spi surface is exempt outright (change adopt-nebula-archrules, design D5). Two
     * reasons compel it. Mechanically, per-source-set evaluation cannot resolve {@code allSubclasses} across
     * a module boundary, and every cross-module base class in the repo ({@code Container}, {@code Accessor},
     * {@code Conversion}) is subclassed only by downstream modules that are not on spi's own classpath.
     * Semantically, on a published extension-point API {@code protected} <em>is</em> the contract offered to
     * third-party strategy authors, not a widening used to dodge Rule A — annotating it
     * {@code @VisibleForTesting} would record a falsehood. Exactly two methods lose coverage:
     * {@code Container#containerOf} and {@code Container#wrapNullness}.
     */
    static final ArchRule UNUSED_PROTECTED_METHODS_ARE_MARKED = methods()
            .that(NOT_SYNTHETIC_OR_BRIDGE)
            .and(NOT_GENERATED)
            .and(DescribedPredicate.describe(
                    "a concrete protected method",
                    (JavaMethod method) -> method.getModifiers().contains(JavaModifier.PROTECTED)
                            && !method.getModifiers().contains(JavaModifier.ABSTRACT)))
            .and(DescribedPredicate.describe(
                    "not on the published spi surface", (JavaMethod method) -> !isPublishedSpi(method.getOwner())))
            .should(new ArchCondition<>("be overridden or called by a subclass, or carry @VisibleForTesting") {
                @Override
                public void check(final JavaMethod method, final ConditionEvents events) {
                    events.add(protectedMethodEvent(method));
                }
            })
            .allowEmptyShould(true)
            .as("Unused protected methods are marked with VisibleForTesting")
            .because("protected is sometimes used purely to dodge the no-private ban without being a real "
                    + "inheritance extension point, and that must be recorded rather than assumed");

    // ---------------------------------------------------------------------------------------------
    // Rule D — no static outside a genuine static context
    // ---------------------------------------------------------------------------------------------

    /**
     * A static method is dispatched by {@code INVOKESTATIC} and so cannot be intercepted by an ordinary test
     * double — the same testability hole Rule A closes for private methods. It was additionally being used
     * to hide a self-call from a Spy's strict {@code 0 * _}, which is what this rule exists to stop.
     */
    static final ArchRule NO_STATIC_OUTSIDE_GENUINE_CONTEXT = methods()
            .that(NOT_SYNTHETIC_OR_BRIDGE)
            .and(NOT_GENERATED)
            .and(DescribedPredicate.describe(
                    "not a public method on the published spi surface",
                    (JavaMethod method) -> !(method.getModifiers().contains(JavaModifier.PUBLIC)
                            && isPublishedSpi(method.getOwner()))))
            .and(DescribedPredicate.describe(
                    "not mandated by a framework or by the JLS", MethodShapeRules::isNotFrameworkMandated))
            .and(DescribedPredicate.describe(
                    "not declared in a stateless all-static utility holder",
                    (JavaMethod method) -> !isUtilityHolder(method.getOwner())))
            .and(DescribedPredicate.describe(
                    "not a named constructor returning its own declaring type",
                    (JavaMethod method) -> !isNamedConstructor(method)))
            .should(new ArchCondition<>("not be static") {
                @Override
                public void check(final JavaMethod method, final ConditionEvents events) {
                    final boolean isStatic = method.getModifiers().contains(JavaModifier.STATIC);
                    final String message = method.getFullName() + (isStatic ? " is" : " is not") + " static";
                    events.add(
                            isStatic
                                    ? SimpleConditionEvent.violated(method, message)
                                    : SimpleConditionEvent.satisfied(method, message));
                }
            })
            .allowEmptyShould(true)
            .as("No method is static outside a genuine static context")
            .because("a static method cannot be intercepted by a test double, so it is not individually "
                    + "testable — prefer a protected instance method, and never use static to hide a "
                    + "self-call from a spied subject");

    static boolean isPublishedSpi(final JavaClass javaClass) {
        final String pkg = javaClass.getPackageName();
        return (Packages.SPI.equals(pkg) || pkg.startsWith(Packages.SPI + "."))
                && !pkg.startsWith(Packages.SPI + ".builtins");
    }

    /** Dagger {@code @Provides}, the compiler-generated enum pair, and a {@code main} entry point. */
    static boolean isNotFrameworkMandated(final JavaMethod method) {
        return !isAnnotatedWithAny(method, Packages.DAGGER_PROVIDES)
                && !isEnumValuesPair(method)
                && !"main".equals(method.getName());
    }

    /**
     * {@code values}/{@code valueOf} are compiler-generated but, unlike lambdas, are NOT flagged synthetic in
     * bytecode, so they have to be named out explicitly.
     */
    static boolean isEnumValuesPair(final JavaMethod method) {
        return method.getOwner().isEnum() && ENUM_GENERATED_METHODS.contains(method.getName());
    }

    /**
     * A static whose whole body constructs and returns its own declaring type (or an interface that type
     * directly implements). There is no instance to hang it on and nothing to intercept — a double over it
     * could only return what the constructor already returns. The rule cannot see whether the body is really
     * just a {@code new}, so this also covers factories that carry logic; those are decomposed by review
     * rather than by this rule, which under-enforces deliberately instead of producing false positives.
     */
    static boolean isNamedConstructor(final JavaMethod method) {
        final JavaClass returned = method.getRawReturnType();
        return returned.equals(method.getOwner())
                || method.getOwner().getRawInterfaces().stream()
                        .anyMatch(i -> i.getName().equals(returned.getName()));
    }

    static ConditionEvent protectedMethodEvent(final JavaMethod method) {
        if (isGenuineExtensionPoint(method)) {
            return SimpleConditionEvent.satisfied(
                    method, method.getFullName() + " is a genuine extension point or annotated @VisibleForTesting");
        }
        return SimpleConditionEvent.violated(
                method, method.getFullName() + " has no subclass usage and no @VisibleForTesting annotation");
    }

    static boolean isGenuineExtensionPoint(final JavaMethod method) {
        return isUsedBySubclass(method) || method.isAnnotatedWith(Packages.VISIBLE_FOR_TESTING);
    }

    static boolean isUsedBySubclass(final JavaMethod method) {
        final Set<JavaClass> subclasses = method.getOwner().getAllSubclasses();
        final boolean overridden = subclasses.stream().anyMatch(subclass -> subclass.getMethods().stream()
                .anyMatch(m -> m.getName().equals(method.getName())
                        && m.getRawParameterTypes().equals(method.getRawParameterTypes())));
        return overridden
                || method.getCallsOfSelf().stream().anyMatch(call -> subclasses.contains(call.getOriginOwner()));
    }

    /**
     * The {@code @UtilityClass} shape, matched structurally: final, every constructor private, no instance
     * field, and nothing but static methods — so the class can never be instantiated and no test double can
     * exist for it. Matched by shape because {@code @UtilityClass} is SOURCE-retention and never reaches the
     * bytecode ArchUnit imports. An enum matches every structural test yet plainly has instances, so it is
     * excluded — which is what keeps {@code Nullability.either} in the violation list. A class declaring no
     * method at all is a value type with nothing to exempt, so an empty method set does not vacuously pass.
     */
    static boolean isUtilityHolder(final JavaClass clazz) {
        return isFinalNonEnum(clazz)
                && hasNoInstanceState(clazz)
                && isUninstantiable(clazz)
                && declaresOnlyStaticMethods(clazz);
    }

    /**
     * An enum matches every other structural test — final, private constructor, constants are static fields
     * — yet plainly has instances, and its own constants are the receiver a helper should hang off.
     */
    static boolean isFinalNonEnum(final JavaClass clazz) {
        return clazz.getModifiers().contains(JavaModifier.FINAL) && !clazz.isEnum();
    }

    static boolean hasNoInstanceState(final JavaClass clazz) {
        return clazz.getFields().stream()
                .allMatch(f -> f.getModifiers().contains(JavaModifier.STATIC)
                        || f.getModifiers().contains(JavaModifier.SYNTHETIC));
    }

    static boolean isUninstantiable(final JavaClass clazz) {
        return !clazz.getConstructors().isEmpty()
                && clazz.getConstructors().stream()
                        .allMatch(c -> c.getModifiers().contains(JavaModifier.PRIVATE)
                                || c.getModifiers().contains(JavaModifier.SYNTHETIC));
    }

    static boolean declaresOnlyStaticMethods(final JavaClass clazz) {
        final List<JavaMethod> authored = clazz.getMethods().stream()
                .filter(m -> !m.getModifiers().contains(JavaModifier.SYNTHETIC)
                        && !GROOVY_METACLASS_ACCESSORS.contains(m.getName()))
                .collect(java.util.stream.Collectors.toList());
        return !authored.isEmpty()
                && authored.stream().allMatch(m -> m.getModifiers().contains(JavaModifier.STATIC));
    }

    /**
     * {@code javax.annotation.processing.Generated} is SOURCE-retention, so ArchUnit's bytecode import never
     * sees it on Dagger's components — {@code dagger.internal.DaggerGenerated} (CLASS-retention) is what
     * survives. It lands on the top-level {@code DaggerXxxComponent} class, not on the nested {@code *Impl}
     * whose methods actually trip Rule A, so the check walks up the enclosing-class chain.
     */
    static boolean isGeneratedOrNestedInGenerated(final JavaClass clazz) {
        return Arrays.stream(Packages.DAGGER_GENERATED).anyMatch(clazz::isAnnotatedWith)
                || clazz.getEnclosingClass()
                        .map(MethodShapeRules::isGeneratedOrNestedInGenerated)
                        .orElse(false);
    }

    static boolean isAnnotatedWithAny(final JavaMethod method, final String... annotationNames) {
        return Arrays.stream(annotationNames).anyMatch(method::isAnnotatedWith);
    }

    @Override
    public Map<String, ArchRule> getRules() {
        return Map.of(
                "no-private-methods", NO_PRIVATE_METHODS,
                "decomposed-class-size-ceiling", DECOMPOSED_CLASSES_STAY_WITHIN_CEILING,
                "unused-protected-marked-visible-for-testing", UNUSED_PROTECTED_METHODS_ARE_MARKED,
                "no-static-outside-genuine-context", NO_STATIC_OUTSIDE_GENUINE_CONTEXT);
    }
}
