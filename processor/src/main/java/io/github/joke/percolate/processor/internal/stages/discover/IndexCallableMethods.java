package io.github.joke.percolate.processor.internal.stages.discover;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.spi.CallableMethods;
import io.github.joke.percolate.spi.MethodCandidate;
import io.github.joke.percolate.spi.ThisReceiver;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;

/**
 * The {@link CallableMethods} view over a mapper's callable candidates: {@link #producing} answers with the candidates
 * whose return type is assignable to the demanded output, each invoked on the mapper itself ({@link ThisReceiver}).
 * Assignability is the one type question it asks, routed through {@link Types#isAssignable} — a single seam call a unit
 * spec stubs while the return-type/output {@link TypeMirror}s stay opaque never-stubbed tokens.
 */
@RequiredArgsConstructor
final class IndexCallableMethods implements CallableMethods {

    private final List<CandidateDescriptor> candidates;
    private final Types types;

    @Override
    public Stream<MethodCandidate> producing(final TypeMirror outputType) {
        return candidates.stream()
                .filter(candidate -> types.isAssignable(candidate.getReturnType(), outputType))
                .map(candidate -> new MethodCandidate(candidate.getMethod(), ThisReceiver.INSTANCE))
                .collect(toUnmodifiableList())
                .stream();
    }
}
