package io.github.joke.percolate.processor.test

import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement

@Tag('unit')
class ExpansionResultSpec extends Specification {

    def 'of produces result with no failure'() {
        expect:
        !newResult(true).hasFailures()
    }

    def 'failed produces result with failure'() {
        when:
        final result = ExpansionResult.failed(new MapperGraph(), [], 0, 'boom', placeholderType())

        then:
        result.hasFailures()
    }

    def 'failed exposes reason'() {
        when:
        final result = ExpansionResult.failed(new MapperGraph(), [], 0, 'boom', placeholderType())

        then:
        result.failureReason() == 'boom'
    }

    def 'empty diagnostics means no errors'() {
        expect:
        !newResult(true).hasErrors()
    }

    def 'non-empty diagnostics means errors'() {
        when:
        final result = ExpansionResult.of(new MapperGraph(), ['error'], 1, false, placeholderType())

        then:
        result.hasErrors()
    }

    def 'converged flag surfaces from of'() {
        expect:
        newResult(true).converged()
    }

    def 'not-converged flag surfaces from of'() {
        expect:
        !newResult(false).converged()
    }

    def 'round count surfaces'() {
        when:
        final result = ExpansionResult.of(new MapperGraph(), [], 5, true, placeholderType())

        then:
        result.roundCount() == 5
    }

    def 'diagnostics list is immutable copy'() {
        given:
        final inputs = ['a', 'b'] as ArrayList

        when:
        final result = ExpansionResult.of(new MapperGraph(), inputs, 0, true, placeholderType())
        inputs.clear()

        then:
        result.diagnostics() == ['a', 'b']
    }

    def 'empty graph has no identity collisions'() {
        expect:
        !newResult(true).hasIdentityCollisions()
    }

    def 'empty graph has no orphan realised nodes'() {
        expect:
        !newResult(true).hasOrphanRealisedNodes()
    }

    def 'graph with reachable realised edge has no orphans'() {
        when:
        final graph = GraphFixtures.graphWithSeedAndRealisedPath()
        final result = ExpansionResult.of(graph, [], 1, true, placeholderType())

        then:
        !result.hasOrphanRealisedNodes()
    }

    def 'graph with orphan realised edge is flagged'() {
        when:
        final graph = GraphFixtures.graphWithOrphanRealisedEdge()
        final result = ExpansionResult.of(graph, [], 1, true, placeholderType())

        then:
        result.hasOrphanRealisedNodes()
    }

    def 'dot render with mapper type produces non-empty'() {
        expect:
        !newResult(true).dotRender().empty
    }

    def 'isIdempotent returns true by contract'() {
        expect:
        newResult(true).idempotent
    }

    private static ExpansionResult newResult(final boolean converged) {
        ExpansionResult.of(new MapperGraph(), [], 1, converged, placeholderType())
    }

    private static TypeElement placeholderType() {
        TypeUniverse.element('java.lang.Object')
    }
}
