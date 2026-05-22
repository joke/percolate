package io.github.joke.percolate.processor.stages.expand.properties.fakes

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.*
import io.github.joke.percolate.spi.test.TypeUniverse

import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * Scope-exiting half of the chain pair. Replaces the legacy fused ChainBridge.
 * Matches when from = elementType and to = outerType, emits a single EXITING
 * BridgeStep that crosses element -> regular scope.
 */
final class ChainExit implements Bridge {

    private static final EdgeCodegen NO_OP_CODEGEN = { vars, inputs -> CodeBlock.of('') }

    private final TypeMirror elementType
    private final TypeMirror outerType

    ChainExit(final TypeMirror elementType, final TypeMirror outerType) {
        this.elementType = Objects.requireNonNull(elementType)
        this.outerType = Objects.requireNonNull(outerType)
    }

    @Override
    Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        if (TypeUniverse.types().isSameType(from, elementType) && TypeUniverse.types().isSameType(to, outerType)) {
            return Stream.of(new BridgeStep(elementType, outerType, 1, NO_OP_CODEGEN, ScopeTransition.EXITING, 'element'))
        }
        Stream.empty()
    }
}
