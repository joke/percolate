package io.github.joke.percolate.processor;

import com.google.auto.common.BasicAnnotationProcessor.Step;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import io.github.joke.percolate.Mapper;
import io.github.joke.percolate.spi.Subjects;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

// The single Step for @Mapper types and the sole round-aware component (stages stay round-agnostic). For each
// mapper it runs the Pipeline and classifies the outcome:
//
//   contract error (the mapper is scarred — bad @Map, duplicate target, unknown source): consume; never
//           defer, since a typo is wrong in every round
//   realised (empty recorded outcome): consume — GenerateStage already emitted
//   unsatisfied realisation (a pure no-producer outcome): defer
//
// Deferring returns the mapper TypeElement so BasicAnnotationProcessor re-resolves it by name
// in a later round. Such a round occurs while an AST-modifying upstream processor (e.g. Lombok in the same
// compilation unit) is still working; by then the injected members are visible and the mapper realises and is
// consumed. A mapper that is still deferred when processing ends is genuinely un-realisable:
// PercolateProcessor.postRound flushes its recorded no plan message on the final round (the Step itself is not
// invoked at processingOver).
//
// The only cross-round state is .deferred, keyed by fully-qualified name and holding strings only — never
// elements (which go stale across rounds); the location is re-resolved by name at flush.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class MapperStep implements Step {

    private static final String MAPPER_FQN = Mapper.class.getCanonicalName();

    private final Pipeline pipeline;
    private final DiagnosticEmitter diagnosticEmitter;
    private final Elements elements;

    // Retained by FQN, message text only — never an Element/javax.lang.model.type.TypeMirror, which go stale across
    // rounds (design D14).
    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded: processing rounds run sequentially
    private final Map<String, List<String>> deferred = new HashMap<>();

    @Override
    public Set<String> annotations() {
        return ImmutableSet.of(MAPPER_FQN);
    }

    @Override
    public Set<? extends Element> process(final ImmutableSetMultimap<String, Element> elementsByAnnotation) {
        return elementsByAnnotation.get(MAPPER_FQN).stream()
                .filter(TypeElement.class::isInstance)
                .map(TypeElement.class::cast)
                .filter(this::processAndShouldDefer)
                .collect(toUnmodifiableSet());
    }

    // Runs the pipeline for one mapper and returns true iff it must be deferred to a later round: a mapper defers
    // iff it recorded at least one error and every recorded error is transient (design D14). Deferring retains only
    // the message text; consuming (whether realised, scarred, or warning-only) flushes every collected diagnostic
    // immediately.
    @VisibleForTesting
    boolean processAndShouldDefer(final TypeElement mapperType) {
        final var ctx = pipeline.process(mapperType);
        final var fqn = mapperType.getQualifiedName().toString();
        final var errors = ctx.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getSeverity() == Diagnostic.Severity.ERROR)
                .collect(toUnmodifiableList());

        if (!errors.isEmpty() && errors.stream().noneMatch(Diagnostic::isPermanent)) {
            deferred.put(fqn, errors.stream().map(Diagnostic::getMessage).collect(toUnmodifiableList()));
            return true;
        }
        deferred.remove(fqn);
        diagnosticEmitter.flush(mapperType, ctx.getDiagnostics());
        return false;
    }

    // Emits the recorded no plan diagnostic for every mapper still deferred when processing ends, re-resolving each
    // location by name. Invoked from PercolateProcessor.postRound on the final round, because
    // BasicAnnotationProcessor does not invoke a Step at processingOver.
    @VisibleForTesting
    void flushDeferredDiagnostics() {
        deferred.forEach(this::flushDeferredFor);
        deferred.clear();
    }

    // Re-resolves fqn and emits its recorded messages as errors; a name that no longer resolves is dropped.
    @VisibleForTesting
    void flushDeferredFor(final String fqn, final List<String> messages) {
        final var location = elements.getTypeElement(fqn);
        if (location == null) {
            return;
        }
        final var stale = messages.stream()
                .map(message -> Diagnostic.error(Subjects.none(), message))
                .collect(toUnmodifiableList());
        diagnosticEmitter.flush(location, stale);
    }
}
