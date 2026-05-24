package io.github.joke.percolate.spi;

import javax.lang.model.type.TypeMirror;
import java.util.Optional;

/**
 * Resolves a single segment of a source path to a typed access. See the {@code source-path-resolution}
 * capability for per-resolver semantics (probe order, codegen shape, weight).
 */
public interface PathSegmentResolver {

    Optional<ResolvedSegment> resolve(TypeMirror parentType, String segment, ResolveCtx ctx);
}
