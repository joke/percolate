package io.github.joke.percolate.test

import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.TypeName
import io.github.joke.percolate.spi.ExpansionStrategy
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.OperationSpec
import io.github.joke.percolate.spi.ProduceDemand
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights

import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * A universal producer for engine-level tests in the processor module. For ANY produce demand it emits a single
 * zero-port operation rendering a type-appropriate sentinel — {@code (T) null} for references, a default literal for
 * primitives — so that, with no real strategy on the classpath, the engine still assembles a compiling mapper and a
 * test can assert it wove the strategy output into the right structural slot without depending on any builtin.
 *
 * <p>It is registered per-consuming-module via {@code META-INF/services}, never globally from this module, so strategy
 * modules keep only their real strategies.
 */
final class FakeStrategy implements ExpansionStrategy {

    static final String SENTINEL_LABEL = 'percolate.fake'

    @Override
    Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final TypeMirror target = demand.targetType()
        final OperationCodegen codegen = { inputs -> sentinel(target) } as OperationCodegen
        Stream.of(OperationSpec.of(SENTINEL_LABEL, codegen, Weights.STEP, [], target, Nullability.NON_NULL))
    }

    private static CodeBlock sentinel(final TypeMirror type) {
        switch (type.kind) {
            case TypeKind.BOOLEAN:
                return CodeBlock.of('false')
            case TypeKind.LONG:
                return CodeBlock.of('0L')
            case TypeKind.FLOAT:
                return CodeBlock.of('0.0f')
            case TypeKind.DOUBLE:
                return CodeBlock.of('0.0d')
            case TypeKind.BYTE:
            case TypeKind.SHORT:
            case TypeKind.INT:
            case TypeKind.CHAR:
                return CodeBlock.of('0')
            default:
                return CodeBlock.of('($T) null', TypeName.get(type))
        }
    }
}
