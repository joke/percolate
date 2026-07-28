package io.github.joke.percolate.processor.internal.stages.generate;

import com.groupcdg.pitest.annotations.CoverageIgnore;
import io.github.joke.percolate.lib.javapoet.TypeName;
import javax.lang.model.type.TypeMirror;

// Renders a TypeMirror to its JavaPoet TypeName (design D7 of change decompose-engine-stages): the irreducible
// compiler-backed leaf behind a hoisted local's declared type — JavaPoet can only render a TypeName from a real
// mirror, so this is the one seam a BuildMethodBodies unit spec mocks; only the compile-based feature-e2e layer
// exercises the real rendering.
@CoverageIgnore
final class TypeNameRenderer {

    TypeName render(final TypeMirror type) {
        return TypeName.get(type);
    }
}
