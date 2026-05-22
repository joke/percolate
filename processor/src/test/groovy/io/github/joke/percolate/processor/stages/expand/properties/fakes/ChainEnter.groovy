package io.github.joke.percolate.processor.stages.expand.properties.fakes

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.*
import io.github.joke.percolate.spi.test.TypeUniverse

import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * Scope-entering half of the chain pair. Replaces the legacy fused ChainBridge.
 * Matches when from = outerType and to = elementType, emits a single ENTERING
 * BridgeStep that crosses regular -> element scope.
 */
final class ChainEnter implements Bridge {

    private static final EdgeCodegen NO_OP_CODEGEN = { vars, inputs -> CodeBlock.of('') }

    private final TypeMirror outerType
    private final TypeMirror elementType

    ChainEnter(final TypeMirror outerType, final TypeMirror elementType) {
        this.outerType = Objects.requireNonNull(outerType)
        this.elementType = Objects.requireNonNull(elementType)
    }

    @Override
    Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        if (TypeUniverse.types().isSameType(from, outerType) && TypeUniverse.types().isSameType(to, elementType)) {
            return Stream.of(new BridgeStep(outerType, elementType, 1, NO_OP_CODEGEN, ScopeTransition.ENTERING, 'element'))
        }
        Stream.empty()
    }
}
