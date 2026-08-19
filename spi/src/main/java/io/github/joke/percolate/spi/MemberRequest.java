package io.github.joke.percolate.spi;

import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.lib.javapoet.TypeName;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A strategy's request for a deduplicated class-level member on the generated mapper type (design D5 of change
 * {@code add-temporal-type-mapping} — the class-scoped sibling of method-scoped local hoisting), in one of two
 * shapes: a {@link Field} initialized once, or a {@link Method} carrying a statement body. The code-generation
 * stage deduplicates member requests by {@code dedupKey} across every method body of the generated type — two
 * requests with an equal {@code dedupKey} share one member. The requesting operation's {@link OperationCodegen}
 * reaches the allocated member's reference through {@link IncomingValues#member(String)}, keyed by the same
 * {@code dedupKey} — the same indirection a hoisted local reaches its codegen through, so the composer holds
 * zero field syntax and zero method syntax.
 *
 * <p>A {@link Method} request is how a strategy emits a <b>statement sequence</b> without a composable statement
 * shape in this SPI (design D1/D2 of change {@code add-setter-assembly}): the statements live in the requested
 * member and the operation still renders one expression — a call to it. The stage dispatches on the shape the
 * strategy declared and never selects a shape of its own, the same rule that lets {@link OperationCodegen} and
 * {@link BodyCodegen} coexist.
 *
 * <p>A strategy that needs an inline (non-shared) value — e.g. a per-call {@code SimpleDateFormat}, which is not
 * thread-safe — declares no member request and renders it inline instead.
 *
 * <p>Construct only via {@link #field(TypeName, CodeBlock, String)} and
 * {@link #method(String, TypeName, List, CodeBlock, String)}.
 */
// Intentional pseudo-sealed base, mirroring Offer's and PortType's Java 11 closed-hierarchy convention: a
// package-private constructor pins membership to the two leaves below, walked structurally by the generate
// stage rather than through a dispatch method.
public abstract class MemberRequest {

    /** Package-private to keep the shape pseudo-sealed: only the leaves below participate. */
    MemberRequest() {}

    /** A field member of {@code fieldType}, initialized once with {@code initializer}. */
    public static MemberRequest field(final TypeName fieldType, final CodeBlock initializer, final String dedupKey) {
        return new Field(fieldType, initializer, dedupKey);
    }

    /**
     * A method member returning {@code returnType} over {@code parameters}, with {@code body} as its complete
     * statement body. {@code nameHint} seeds the class-scope name allocation: the stage still owns the final
     * name, so a hint that collides is disambiguated rather than honoured verbatim.
     */
    public static MemberRequest method(
            final String nameHint,
            final TypeName returnType,
            final List<Parameter> parameters,
            final CodeBlock body,
            final String dedupKey) {
        return new Method(nameHint, returnType, parameters, body, dedupKey);
    }

    /** The content key this request deduplicates by across every method body of the generated type. */
    public abstract String getDedupKey();

    /** One parameter of a {@link Method} request: its type and the name its body refers to it by. */
    @Value
    public static class Parameter {
        TypeName type;
        String name;
    }

    /** A field leaf: emitted once with the requested type and initializer. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static final class Field extends MemberRequest {
        TypeName fieldType;
        CodeBlock initializer;
        String dedupKey;
    }

    /** A method leaf: emitted once with the requested return type, parameters and body. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static final class Method extends MemberRequest {
        String nameHint;
        TypeName returnType;
        List<Parameter> parameters;
        CodeBlock body;
        String dedupKey;

        Method(
                final String nameHint,
                final TypeName returnType,
                final List<Parameter> parameters,
                final CodeBlock body,
                final String dedupKey) {
            super();
            this.nameHint = nameHint;
            this.returnType = returnType;
            this.parameters = List.copyOf(parameters);
            this.body = body;
            this.dedupKey = dedupKey;
        }
    }
}
