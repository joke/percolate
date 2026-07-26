package io.github.joke.percolate.processor;

import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

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

/**
 * The single {@link Step} for {@code @Mapper} types and the sole round-aware component (stages stay
 * round-agnostic). For each mapper it runs the {@link Pipeline} and classifies the outcome:
 *
 * <ul>
 *   <li><b>contract error</b> (the mapper is scarred — bad {@code @Map}, duplicate target, unknown
 *       source): consume; never defer (a typo is wrong in every round);
 *   <li><b>realised</b> (empty recorded outcome): consume — {@code GenerateStage} already emitted;
 *   <li><b>unsatisfied realisation</b> (a pure no-producer outcome): defer.
 * </ul>
 *
 * <p>Deferring returns the mapper {@code TypeElement} so {@code BasicAnnotationProcessor} re-resolves
 * it by name in a later round. Such a round occurs while an AST-modifying upstream processor (e.g.
 * Lombok in the same compilation unit) is still working; by then the injected members are visible and
 * the mapper realises and is consumed. A mapper that is <em>still</em> deferred when processing ends
 * is genuinely un-realisable: {@code PercolateProcessor.postRound} flushes its recorded {@code no plan}
 * message on the final round (the {@code Step} itself is not invoked at {@code processingOver}).
 *
 * <p>The only cross-round state is {@link #deferred}, keyed by fully-qualified name and holding strings
 * only — never elements (which go stale across rounds); the location is re-resolved by name at flush.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class MapperStep implements Step {

    private static final String MAPPER_FQN = Mapper.class.getCanonicalName();

    private final Pipeline pipeline;
    private final DiagnosticEmitter diagnosticEmitter;
    private final Elements elements;

    /** Retained by FQN, message text only — never an {@link Element}/{@link javax.lang.model.type.TypeMirror}, which go stale across rounds (design D14). */
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

    /**
     * Runs the pipeline for one mapper and returns {@code true} iff it must be deferred to a later round: a mapper
     * defers iff it recorded at least one error and every recorded error is transient (design D14). Deferring
     * retains only the message text; consuming (whether realised, scarred, or warning-only) flushes every
     * collected diagnostic immediately.
     */
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

    /**
     * Emits the recorded {@code no plan} diagnostic for every mapper still deferred when processing
     * ends, re-resolving each location by name. Invoked from {@code PercolateProcessor.postRound} on
     * the final round, because {@code BasicAnnotationProcessor} does not invoke a {@code Step} at
     * {@code processingOver}.
     */
    void flushDeferredDiagnostics() {
        deferred.forEach((fqn, messages) -> {
            final var location = elements.getTypeElement(fqn);
            if (location != null) {
                final var stale = messages.stream()
                        .map(message -> Diagnostic.error(Subjects.none(), message))
                        .collect(toUnmodifiableList());
                diagnosticEmitter.flush(location, stale);
            }
        });
        deferred.clear();
    }
}
