package io.github.joke.percolate.processor.test

import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.processor.test.ExpansionAssertions.ExpansionAssert.DiagnosticKind
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement

@Tag('unit')
class ExpansionAssertionsSpec extends Specification {

    def 'no-path diagnostic fails when no diagnostics present'() {
        when:
        ExpansionAssertions.assertThat(noErrors()).reportedError(DiagnosticKind.NO_PATH)

        then:
        thrown(AssertionError)
    }

    def 'no-path diagnostic passes when message present'() {
        when:
        ExpansionAssertions.assertThat(withDiagnostic('No realised path here')).reportedError(DiagnosticKind.NO_PATH)

        then:
        noExceptionThrown()
    }

    def 'cycle diagnostic matches keyword'() {
        when:
        ExpansionAssertions.assertThat(withDiagnostic('Cycle detected at scope')).reportedError(DiagnosticKind.CYCLE)

        then:
        noExceptionThrown()
    }

    def 'round-cap diagnostic matches converge keyword'() {
        when:
        ExpansionAssertions.assertThat(withDiagnostic('did not converge')).reportedError(DiagnosticKind.ROUND_CAP)

        then:
        noExceptionThrown()
    }

    def 'reachable fails when source missing'() {
        when:
        ExpansionAssertions.assertThat(noErrors()).reachable('missingSource', 'missingTarget')

        then:
        thrown(AssertionError)
    }

    def 'chain forSeedEdge fails when no matching message'() {
        when:
        ExpansionAssertions.assertThat(withDiagnostic('No realised path between A and B'))
                .reportedError(DiagnosticKind.NO_PATH)
                .forSeedEdge('X', 'Y')

        then:
        thrown(AssertionError)
    }

    def 'chain forSeedEdge passes when message contains endpoint'() {
        when:
        ExpansionAssertions.assertThat(withDiagnostic('No realised path between SOURCE and TARGET'))
                .reportedError(DiagnosticKind.NO_PATH)
                .forSeedEdge('SOURCE', 'TARGET')

        then:
        noExceptionThrown()
    }

    def 'no-path diagnostic matches lowercase variant'() {
        when:
        ExpansionAssertions.assertThat(withDiagnostic('no realised path here')).reportedError(DiagnosticKind.NO_PATH)

        then:
        noExceptionThrown()
    }

    def 'cycle diagnostic matches lowercase variant'() {
        when:
        ExpansionAssertions.assertThat(withDiagnostic('oh look, a cycle')).reportedError(DiagnosticKind.CYCLE)

        then:
        noExceptionThrown()
    }

    def 'round-cap diagnostic matches round keyword'() {
        when:
        ExpansionAssertions.assertThat(withDiagnostic('exceeded round budget')).reportedError(DiagnosticKind.ROUND_CAP)

        then:
        noExceptionThrown()
    }

    def 'reachable succeeds with realised path'() {
        given:
        final graph = GraphFixtures.graphWithSeedAndRealisedPath()
        final result = ExpansionResult.of(graph, [], 1, true, placeholderType())

        when:
        ExpansionAssertions.assertThat(result).reachable('in', 'out')

        then:
        noExceptionThrown()
    }

    def 'reachable fails when no realised edge'() {
        when:
        ExpansionAssertions.assertThat(noErrors()).reachable('nothing', 'nowhere')

        then:
        thrown(AssertionError)
    }

    private static ExpansionResult noErrors() {
        ExpansionResult.of(new MapperGraph(), [], 0, true, placeholderType())
    }

    private static ExpansionResult withDiagnostic(final String message) {
        ExpansionResult.of(new MapperGraph(), [message], 0, false, placeholderType())
    }

    private static TypeElement placeholderType() {
        TypeUniverse.element('java.lang.Object')
    }
}
