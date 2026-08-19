package io.github.joke.percolate.spi.builtins.assembly;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.lib.javapoet.NameAllocator;
import io.github.joke.percolate.lib.javapoet.TypeName;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.IncomingValues;
import io.github.joke.percolate.spi.MemberRequest;
import io.github.joke.percolate.spi.Offer;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.spi.Nullability.NON_NULL;
import static io.github.joke.percolate.spi.Port.subTarget;
import static io.github.joke.percolate.spi.builtins.assembly.ConstructionPreference.SETTER;
import static io.github.joke.percolate.spi.builtins.assembly.ConstructionPreference.weightOf;
import static java.lang.Character.toUpperCase;
import static java.lang.String.join;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.IntStream.range;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.Modifier.ABSTRACT;

/**
 * Assembles the demanded type as a plain JavaBean: its no-argument constructor followed by one {@code setX} call
 * per declared child (change {@code add-setter-assembly}).
 *
 * <p>Like every assembly form it emits exactly <b>one</b> n-ary {@link OperationSpec} carrying one
 * {@link Port#subTarget} per declared child. That is load-bearing rather than stylistic: totality is enforced
 * only by sub-target ports on a single operation, since an unsatisfied port makes the plan partial and
 * {@code Cost} is the lexicographic {@code (partials, weight)} vector in which partials dominate absolutely.
 * Per-setter operations would make an omitted setter a <i>cheaper</i> plan rather than a partial one, so the
 * minimum-cost fold would drop a declared mapping in silence.
 *
 * <p>The setter calls are statements, and every {@link OperationCodegen} renders one expression. They therefore
 * live in a generated helper method the operation requests through {@link MemberRequest#method}, and the
 * operation renders one call to it. No composable statement shape enters the SPI and the engine gains no setter
 * vocabulary.
 *
 * <p>The gate is <b>containment</b>, not the set equality {@link ConstructorCall} uses: a bean normally exposes
 * far more setters than a mapping declares, and the surplus ones are left uncalled. The empty-declaration bail is
 * kept, so an empty declared set can never satisfy a leaf demand through a no-argument constructor that happens
 * to exist.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class SetterAssembly implements ExpansionStrategy {

    private static final String SETTER_PREFIX = "set";
    private static final String DEDUP_PREFIX = "setter:";
    private static final String NAME_HINT_PREFIX = "assemble";
    private static final String RESULT_BASE = "result";

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var targetType = demand.targetType();
        final var targetElement = ctx.asTypeElement(targetType).orElse(null);
        if (targetElement == null) {
            return Stream.empty();
        }
        final var declared = List.copyOf(demand.declaredChildren());
        if (declared.isEmpty()) {
            // A leaf demand (no declared children) is never assembled: a no-argument constructor must not
            // vacuously satisfy it through a bean that happens to expose one.
            return Stream.empty();
        }
        return offer(targetType, targetElement, declared, demand, ctx).stream();
    }

    // The whole match in one place: an instantiable target, its no-argument constructor, then the containment gate.
    @VisibleForTesting
    Optional<Offer> offer(
            final TypeMirror targetType,
            final TypeElement targetElement,
            final List<String> declared,
            final ProduceDemand demand,
            final ResolveCtx ctx) {
        if (!isInstantiable(targetElement) || !hasNoArgConstructor(targetElement, ctx)) {
            return Optional.empty();
        }
        return setters(targetElement, declared, ctx)
                .map(matched -> buildSpec(targetType, targetElement, declared, matched, demand, ctx))
                .map(Offer::of);
    }

    // A bean must be a concrete class: an interface has no constructor at all, and an abstract class cannot be
    // instantiated even when it declares one.
    @VisibleForTesting
    boolean isInstantiable(final TypeElement targetElement) {
        return targetElement.getKind() == CLASS && !targetElement.getModifiers().contains(ABSTRACT);
    }

    @VisibleForTesting
    boolean hasNoArgConstructor(final TypeElement targetElement, final ResolveCtx ctx) {
        return ctx.membersOf(targetElement)
                .filter(ctx::isConstructor)
                .filter(member -> !ctx.isPrivate(member))
                .anyMatch(member -> ((ExecutableElement) member).getParameters().isEmpty());
    }

    // The containment gate: every declared child matched to a setter, in declared order, or empty when any child
    // has none. Order is the demand's own iteration order, which is insertion-ordered for determinism.
    @VisibleForTesting
    Optional<List<ExecutableElement>> setters(
            final TypeElement targetElement, final List<String> declared, final ResolveCtx ctx) {
        final var matched = declared.stream()
                .flatMap(child -> setter(targetElement, child, ctx).stream())
                .collect(toUnmodifiableList());
        return matched.size() == declared.size() ? Optional.of(matched) : Optional.empty();
    }

    // The target's non-private, single-argument setX method feeding child, or empty. The return type is NOT part
    // of the match: a JavaBean setter returns void, some return this, and the helper discards either.
    @VisibleForTesting
    Optional<ExecutableElement> setter(final TypeElement targetElement, final String child, final ResolveCtx ctx) {
        final var wanted = setterName(child);
        return ctx.membersOf(targetElement)
                .flatMap(member -> singleArgMethodNamed(member, wanted, ctx).stream())
                .findFirst();
    }

    /** The JavaBean mutator name for {@code child} — the only convention this strategy recognises. */
    @VisibleForTesting
    String setterName(final String child) {
        return child.isEmpty() ? SETTER_PREFIX : SETTER_PREFIX + toUpperCase(child.charAt(0)) + child.substring(1);
    }

    // member as a non-private, single-argument method named exactly name, else empty.
    @VisibleForTesting
    Optional<ExecutableElement> singleArgMethodNamed(final Element member, final String name, final ResolveCtx ctx) {
        return methodNamed(member, name, ctx)
                .filter(method -> method.getParameters().size() == 1);
    }

    // member as a non-private method named exactly name, else empty.
    @VisibleForTesting
    Optional<ExecutableElement> methodNamed(final Element member, final String name, final ResolveCtx ctx) {
        if (!ctx.isMethod(member) || ctx.isPrivate(member)) {
            return Optional.empty();
        }
        final var method = (ExecutableElement) member;
        return method.getSimpleName().contentEquals(name) ? Optional.of(method) : Optional.empty();
    }

    @VisibleForTesting
    OperationSpec buildSpec(
            final TypeMirror targetType,
            final TypeElement targetElement,
            final List<String> declared,
            final List<ExecutableElement> matched,
            final ProduceDemand demand,
            final ResolveCtx ctx) {
        final var ports = ports(declared, matched, demand);
        final var request = memberRequest(targetType, targetElement, declared, matched);
        return OperationSpec.of(
                        label(targetElement, declared, matched),
                        codegen(declared, request.getDedupKey()),
                        weightOf(SETTER, ctx.option(ConstructionPreference.KEY)),
                        ports,
                        targetType,
                        NON_NULL)
                .withMemberRequests(List.of(request));
    }

    // One sub-target port per declared child, named after the CHILD and typed from its setter's parameter. The
    // sub-target port is what forces the child to be produced: leave it unsatisfied and the plan is partial.
    @VisibleForTesting
    List<Port> ports(final List<String> declared, final List<ExecutableElement> matched, final ProduceDemand demand) {
        return range(0, declared.size())
                .mapToObj(i -> port(declared.get(i), matched.get(i), demand))
                .collect(toUnmodifiableList());
    }

    @VisibleForTesting
    Port port(final String child, final ExecutableElement setter, final ProduceDemand demand) {
        final var parameter = setter.getParameters().get(0);
        final var type = parameter.asType();
        return subTarget(child, type, demand.nullnessOf(type, parameter));
    }

    /**
     * The helper this assembly runs its setter statements in. Its dedup key carries the assembly <b>form</b>
     * alongside the target and the ordered children, so a later form producing the same target from the same
     * children requests a distinct member rather than renaming this one.
     */
    @VisibleForTesting
    MemberRequest memberRequest(
            final TypeMirror targetType,
            final TypeElement targetElement,
            final List<String> declared,
            final List<ExecutableElement> matched) {
        final var names = new NameAllocator();
        final var parameterNames = declared.stream().map(names::newName).collect(toUnmodifiableList());
        final var resultName = names.newName(RESULT_BASE);
        final var returnType = TypeName.get(targetType);
        return MemberRequest.method(
                NAME_HINT_PREFIX + targetElement.getSimpleName(),
                returnType,
                parameters(parameterNames, matched),
                body(returnType, resultName, parameterNames, matched),
                dedupKey(targetType, declared));
    }

    @VisibleForTesting
    List<MemberRequest.Parameter> parameters(final List<String> parameterNames, final List<ExecutableElement> matched) {
        return range(0, parameterNames.size())
                .mapToObj(i -> new MemberRequest.Parameter(
                        TypeName.get(matched.get(i).getParameters().get(0).asType()), parameterNames.get(i)))
                .collect(toUnmodifiableList());
    }

    // new T(); one setX per declared child, in declared order; return it.
    @VisibleForTesting
    CodeBlock body(
            final TypeName returnType,
            final String resultName,
            final List<String> parameterNames,
            final List<ExecutableElement> matched) {
        final var body = CodeBlock.builder().addStatement("$T $N = new $T()", returnType, resultName, returnType);
        range(0, matched.size())
                .forEach(i -> body.addStatement(
                        "$N.$N($N)", resultName, matched.get(i).getSimpleName().toString(), parameterNames.get(i)));
        return body.addStatement("return $N", resultName).build();
    }

    @VisibleForTesting
    String dedupKey(final TypeMirror targetType, final List<String> declared) {
        return DEDUP_PREFIX + targetType + ":" + join(",", declared);
    }

    @VisibleForTesting
    String label(final TypeElement targetElement, final List<String> declared, final List<ExecutableElement> matched) {
        final var setters = matched.stream()
                .map(setter -> setter.getSimpleName().toString())
                .collect(joining(", "));
        return "new " + targetElement.getSimpleName() + "()." + setters;
    }

    @VisibleForTesting
    OperationCodegen codegen(final List<String> declared, final String dedupKey) {
        return inputs -> renderCall(declared, dedupKey, inputs);
    }

    // The whole assembly as ONE expression: a call to the requested helper, whose name the generate stage
    // allocated and hands back through IncomingValues.member.
    @SuppressWarnings("PMD.UseStaticImports")
    @VisibleForTesting
    CodeBlock renderCall(final List<String> declared, final String dedupKey, final IncomingValues inputs) {
        final var args = declared.stream().map(inputs::byName).collect(CodeBlock.joining(", "));
        return CodeBlock.of("$L($L)", inputs.member(dedupKey), args);
    }
}
