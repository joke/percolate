package io.github.joke.percolate.spi;

import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.lib.javapoet.TypeName;
import lombok.Value;

/**
 * A strategy's request for a deduplicated {@code private static final} member on the generated mapper type (design
 * D5 of change {@code add-temporal-type-mapping} — the class-scoped sibling of method-scoped local hoisting): a
 * field of {@code fieldType} initialized once with {@code initializer}. The code-generation stage deduplicates
 * member requests by {@code dedupKey} across every method body of the generated type — two requests with an equal
 * {@code dedupKey} share one field. The requesting operation's {@link OperationCodegen} reaches the allocated
 * field's reference through {@link IncomingValues#member(String)}, keyed by the same {@code dedupKey} — the same
 * indirection a hoisted local reaches its codegen through, so the composer holds zero field syntax.
 *
 * <p>A strategy that needs an inline (non-shared) value — e.g. a per-call {@code SimpleDateFormat}, which is not
 * thread-safe — declares no member request and renders it inline instead.
 */
@Value
public class MemberRequest {
    TypeName fieldType;
    CodeBlock initializer;
    String dedupKey;
}
