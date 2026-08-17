package io.github.joke.percolate.spi.builtins.assembly;

import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.IncomingValues;
import io.github.joke.percolate.spi.Offer;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.builtins.Labels;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.spi.Nullability.NON_NULL;
import static io.github.joke.percolate.spi.Port.subTarget;
import static io.github.joke.percolate.spi.Weights.EXPENSIVE;
import static io.github.joke.percolate.spi.Weights.STEP;
import static io.github.joke.percolate.spi.builtins.assembly.ConstructionPreference.BUILDER;
import static java.lang.Character.toUpperCase;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.IntStream.range;

/**
 * The shape every builder convention shares, extracted from the four shipped ones once their duplication was real
 * (the way {@code Accessor} was extracted from the three path resolvers). It lives in
 * {@code percolate-strategies-builtin}, <b>not</b> in {@code percolate-spi}: a third party adds a convention by
 * shipping its own {@code @AutoService(ExpansionStrategy.class)} implementation, so the SPI gains no builder-named
 * type.
 *
 * <p>It emits <b>one</b> n-ary {@link OperationSpec} with a {@link Port#subTarget} per declared child, never a chain
 * of per-setter operations. That is load-bearing: totality is enforced only by sub-target ports on a single
 * operation — an unsatisfied port makes the plan partial, and partials dominate the lexicographic cost — so
 * decomposing the setters into chained steps would let the minimum-cost fold silently drop a declared mapping by
 * taking a shorter, cheaper chain. From the engine's point of view a builder is indistinguishable from a
 * constructor call.
 *
 * <p>The gate is <b>containment</b>, not the equality {@code ConstructorCall} uses: builder setters are optional, so
 * the demand's declared children need only be a subset of the setters the builder offers. The empty-declaration bail
 * is kept, so an empty declaration never vacuously assembles a leaf demand.
 *
 * <p>A subclass supplies only its convention: where the builder comes from ({@link #builderFor}), how the chain opens
 * ({@link #entryCall}), how that reads in a debug label ({@link #labelHead}), and how a declared child's name maps to
 * a builder method ({@link #setterName}).
 */
public abstract class BuilderAssembly implements ExpansionStrategy {

    private static final String BUILD = "build";

    @Override
    public final Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var targetType = demand.targetType();
        final var targetElement = ctx.asTypeElement(targetType).orElse(null);
        if (targetElement == null) {
            return Stream.empty();
        }
        final var declared = List.copyOf(demand.declaredChildren());
        if (declared.isEmpty()) {
            // A leaf demand (no declared children) is never assembled: an empty declaration must not vacuously
            // satisfy it through a builder that happens to exist.
            return Stream.empty();
        }
        return offer(targetType, targetElement, declared, demand, ctx).stream();
    }

    /** The builder type this convention finds for {@code targetElement}, or empty when it does not apply. */
    @OverrideOnly
    protected abstract Optional<TypeElement> builderFor(TypeElement targetElement, ResolveCtx ctx);

    /** Renders the call that opens the chain — a static factory on the target, or a builder construction. */
    @OverrideOnly
    protected abstract CodeBlock entryCall(TypeElement targetElement, TypeElement builderElement);

    /** How the chain opener reads in a debug label, e.g. {@code Person.builder}. */
    @OverrideOnly
    protected abstract String labelHead(TypeElement targetElement, TypeElement builderElement);

    /** The builder method feeding {@code child}. Defaults to the child's own name, as fluent builders use. */
    @OverrideOnly
    protected String setterName(final String child) {
        return child;
    }

    /** {@code prefix} applied to {@code child} in camel case — the shared half of every prefix convention. */
    @VisibleForTesting
    protected String prefixed(final String prefix, final String child) {
        return child.isEmpty() ? prefix : prefix + toUpperCase(child.charAt(0)) + child.substring(1);
    }

    // The whole match in one place: the convention's builder, its terminal build(), then the containment gate.
    @VisibleForTesting
    protected Optional<Offer> offer(
            final TypeMirror targetType,
            final TypeElement targetElement,
            final List<String> declared,
            final ProduceDemand demand,
            final ResolveCtx ctx) {
        return builderFor(targetElement, ctx)
                .filter(builder -> hasBuild(builder, targetType, ctx))
                .flatMap(builder -> setters(builder, declared, ctx)
                        .map(matched -> buildSpec(targetType, targetElement, builder, declared, matched, demand, ctx)))
                .map(Offer::of);
    }

    // Whether builder carries a non-private, no-argument build() producing the demanded target.
    @VisibleForTesting
    protected boolean hasBuild(final TypeElement builder, final TypeMirror targetType, final ResolveCtx ctx) {
        return ctx.membersOf(builder)
                .flatMap(member -> noArgMethodNamed(member, BUILD, ctx).stream())
                .anyMatch(build -> ctx.isAssignable(ctx.erasure(build.getReturnType()), ctx.erasure(targetType)));
    }

    // The containment gate: every declared child matched to a setter, in declared order, or empty when any child
    // has none. Order is the demand's own iteration order, which is insertion-ordered for determinism.
    @VisibleForTesting
    protected Optional<List<ExecutableElement>> setters(
            final TypeElement builder, final List<String> declared, final ResolveCtx ctx) {
        final var matched = declared.stream()
                .flatMap(child -> setter(builder, child, ctx).stream())
                .collect(toUnmodifiableList());
        return matched.size() == declared.size() ? Optional.of(matched) : Optional.empty();
    }

    // The builder's non-private, single-argument, self-returning method feeding child, or empty.
    @VisibleForTesting
    protected Optional<ExecutableElement> setter(final TypeElement builder, final String child, final ResolveCtx ctx) {
        final var wanted = setterName(child);
        return ctx.membersOf(builder)
                .flatMap(member -> singleArgMethodNamed(member, wanted, ctx).stream())
                .filter(method -> returnsBuilder(method, builder, ctx))
                .findFirst();
    }

    // A fluent setter returns the builder itself (erasure-compared, so a self-typed generic builder matches).
    @VisibleForTesting
    protected boolean returnsBuilder(final ExecutableElement method, final TypeElement builder, final ResolveCtx ctx) {
        return ctx.isAssignable(ctx.erasure(method.getReturnType()), ctx.erasure(builder.asType()));
    }

    // member as a non-private, no-argument method named exactly name, else empty.
    @VisibleForTesting
    protected Optional<ExecutableElement> noArgMethodNamed(
            final Element member, final String name, final ResolveCtx ctx) {
        return methodNamed(member, name, ctx)
                .filter(method -> method.getParameters().isEmpty());
    }

    // member as a non-private, single-argument method named exactly name, else empty.
    @VisibleForTesting
    protected Optional<ExecutableElement> singleArgMethodNamed(
            final Element member, final String name, final ResolveCtx ctx) {
        return methodNamed(member, name, ctx)
                .filter(method -> method.getParameters().size() == 1);
    }

    @VisibleForTesting
    protected Optional<ExecutableElement> methodNamed(final Element member, final String name, final ResolveCtx ctx) {
        if (!ctx.isMethod(member) || ctx.isPrivate(member)) {
            return Optional.empty();
        }
        final var method = (ExecutableElement) member;
        return method.getSimpleName().contentEquals(name) ? Optional.of(method) : Optional.empty();
    }

    // The target's static, non-private, no-argument factory named name — the shared half of every static-factory
    // convention (builder(), newBuilder()).
    @VisibleForTesting
    protected Optional<TypeElement> staticFactoryBuilder(
            final TypeElement targetElement, final String name, final ResolveCtx ctx) {
        return ctx.membersOf(targetElement)
                .flatMap(member -> noArgMethodNamed(member, name, ctx).stream())
                .filter(ctx::isStatic)
                .findFirst()
                .flatMap(entry -> ctx.asTypeElement(entry.getReturnType()))
                .filter(builder -> !ctx.isPrivate(builder));
    }

    @VisibleForTesting
    protected OperationSpec buildSpec(
            final TypeMirror targetType,
            final TypeElement targetElement,
            final TypeElement builderElement,
            final List<String> declared,
            final List<ExecutableElement> matched,
            final ProduceDemand demand,
            final ResolveCtx ctx) {
        final var ports = ports(declared, matched, demand);
        final var setterNames = matched.stream()
                .map(setter -> setter.getSimpleName().toString())
                .collect(toUnmodifiableList());
        return OperationSpec.of(
                label(targetElement, builderElement, ports),
                buildCodegen(targetElement, builderElement, setterNames, declared),
                weight(ctx),
                ports,
                targetType,
                NON_NULL);
    }

    // One sub-target port per declared child, named after the CHILD (not the setter) and typed from the setter's
    // parameter. The sub-target port is what forces the child to be produced: leave it unsatisfied and the plan is
    // partial. Pairing by position keeps the child name authoritative, so no convention has to invert its own
    // setter naming.
    @VisibleForTesting
    protected List<Port> ports(
            final List<String> declared, final List<ExecutableElement> matched, final ProduceDemand demand) {
        return range(0, declared.size())
                .mapToObj(i -> port(declared.get(i), matched.get(i), demand))
                .collect(toUnmodifiableList());
    }

    @VisibleForTesting
    protected Port port(final String child, final ExecutableElement setter, final ProduceDemand demand) {
        final var parameter = setter.getParameters().get(0);
        final var type = parameter.asType();
        return subTarget(child, type, demand.nullnessOf(type, parameter));
    }

    // Prices this strategy against the author's declared construction preference, inverse to ConstructorCall. The
    // plan fold is minimum-cost, so the preferred form takes the lower weight. It reads only the option and never
    // inspects another strategy — myopia holds.
    @VisibleForTesting
    protected int weight(final ResolveCtx ctx) {
        return ConstructionPreference.from(ctx.option(ConstructionPreference.KEY)) == BUILDER ? STEP : EXPENSIVE;
    }

    @VisibleForTesting
    protected String label(final TypeElement targetElement, final TypeElement builderElement, final List<Port> ports) {
        final var params =
                ports.stream().map(port -> Labels.simple(port.getType())).collect(joining(", "));
        return labelHead(targetElement, builderElement) + "(" + params + ").build()";
    }

    @VisibleForTesting
    protected OperationCodegen buildCodegen(
            final TypeElement targetElement,
            final TypeElement builderElement,
            final List<String> setterNames,
            final List<String> declared) {
        return inputs -> renderChain(targetElement, builderElement, setterNames, declared, inputs);
    }

    // The whole assembly as ONE expression. Every chain continuation carries a $Z wrap marker so a long generated
    // chain wraps at its call boundaries.
    @VisibleForTesting
    protected CodeBlock renderChain(
            final TypeElement targetElement,
            final TypeElement builderElement,
            final List<String> setterNames,
            final List<String> declared,
            final IncomingValues inputs) {
        final var chain = CodeBlock.builder().add(entryCall(targetElement, builderElement));
        for (var i = 0; i < setterNames.size(); i++) {
            chain.add("$Z.$N($L)", setterNames.get(i), inputs.byName(declared.get(i)));
        }
        return chain.add("$Z.$N()", BUILD).build();
    }
}
