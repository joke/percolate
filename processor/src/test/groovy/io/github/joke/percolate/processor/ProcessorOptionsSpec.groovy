package io.github.joke.percolate.processor

import spock.lang.Specification
import spock.lang.Tag

import java.util.Map

@Tag('unit')
class ProcessorOptionsSpec extends Specification {

    def 'debugGraphs is false when option is absent'() {
        given:
        def options = Map.of()

        when:
        def result = ProcessorOptions.from(options)

        then:
        !result.debugGraphs
    }

    def 'debugGraphs is false when option is empty string'() {
        given:
        def options = Map.of('percolate.debug.graphs', '')

        when:
        def result = ProcessorOptions.from(options)

        then:
        !result.debugGraphs
    }

    def 'debugGraphs is true when option is "true"'() {
        given:
        def options = Map.of('percolate.debug.graphs', 'true')

        when:
        def result = ProcessorOptions.from(options)

        then:
        result.debugGraphs
    }

    def 'debugGraphs is true when option is "TRUE" (case-insensitive)'() {
        given:
        def options = Map.of('percolate.debug.graphs', 'TRUE')

        when:
        def result = ProcessorOptions.from(options)

        then:
        result.debugGraphs
    }

    def 'debugGraphs is false when option is "yes"'() {
        given:
        def options = Map.of('percolate.debug.graphs', 'yes')

        when:
        def result = ProcessorOptions.from(options)

        then:
        !result.debugGraphs
    }

    def 'debugGraphs is false when option is "false"'() {
        given:
        def options = Map.of('percolate.debug.graphs', 'false')

        when:
        def result = ProcessorOptions.from(options)

        then:
        !result.debugGraphs
    }
}
