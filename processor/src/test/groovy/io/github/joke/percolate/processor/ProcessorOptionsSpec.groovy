package io.github.joke.percolate.processor

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.SourceVersion

@Tag('unit')
class ProcessorOptionsSpec extends Specification {

    def 'absent percolate.nullable.annotations yields empty set'() {
        when:
        def options = ProcessorOptions.from([:])

        then:
        options.customNullableAnnotations == [] as Set
    }

    def 'single FQN parses to singleton set'() {
        when:
        def options = ProcessorOptions.from([
                'percolate.nullable.annotations': 'com.example.Nullable'
        ])

        then:
        options.customNullableAnnotations == ['com.example.Nullable'] as Set
    }

    def 'comma-separated FQNs yield each entry'() {
        when:
        def options = ProcessorOptions.from([
                'percolate.nullable.annotations': 'com.example.Nullable,org.foo.Optional'
        ])

        then:
        options.customNullableAnnotations == ['com.example.Nullable', 'org.foo.Optional'] as Set
    }

    def 'empty value yields empty set'() {
        when:
        def options = ProcessorOptions.from(['percolate.nullable.annotations': ''])

        then:
        options.customNullableAnnotations == [] as Set
    }

    def 'blank entries between and after commas are dropped'() {
        when:
        def options = ProcessorOptions.from(['percolate.nullable.annotations': 'a,,b,'])

        then:
        options.customNullableAnnotations == ['a', 'b'] as Set
    }

    def 'the custom nullable set is an immutable copy decoupled from the caller-supplied set'() {
        given:
        def input = ['com.example.Nullable'] as Set
        def options = new ProcessorOptions(false, input, false, false, false)

        when:
        input << 'added.after.construction'

        then:
        options.customNullableAnnotations == ['com.example.Nullable'] as Set

        when:
        options.customNullableAnnotations << 'x'

        then:
        thrown(UnsupportedOperationException)
    }

    def 'debug.graphs and docTags default to false when absent'() {
        when:
        def options = ProcessorOptions.from([:])

        then:
        !options.debugGraphs
        !options.docTags
    }

    def 'percolate.debug.graphs and percolate.docTags parse the true flag'() {
        when:
        def options = ProcessorOptions.from([
                'percolate.debug.graphs': 'true',
                'percolate.docTags'     : 'true'
        ])

        then:
        options.debugGraphs
        options.docTags
    }

    def 'flags parse case-insensitively'() {
        expect:
        ProcessorOptions.from(['percolate.locals.final': 'TRUE']).localsFinal
        ProcessorOptions.from(['percolate.docTags': 'True']).docTags
    }

    def 'an unrecognised flag value is treated as false'() {
        expect:
        !ProcessorOptions.from(['percolate.debug.graphs': 'yes']).debugGraphs
    }

    def 'locals.final and locals.var default to false when absent'() {
        when:
        def options = ProcessorOptions.from([:])

        then:
        !options.localsFinal
        !options.localsVar
    }

    def 'percolate.locals.final and percolate.locals.var parse the true flag'() {
        when:
        def options = ProcessorOptions.from([
                'percolate.locals.final': 'true',
                'percolate.locals.var'  : 'true'
        ])

        then:
        options.localsFinal
        options.localsVar
    }

    def 'PercolateProcessor advertises exactly the five recognised options'() {
        expect:
        new PercolateProcessor().supportedOptions == [
                'percolate.debug.graphs',
                'percolate.nullable.annotations',
                'percolate.locals.final',
                'percolate.locals.var',
                'percolate.docTags'
        ] as Set
    }

    def 'PercolateProcessor supports the latest source version the compiler offers'() {
        expect:
        new PercolateProcessor().supportedSourceVersion == SourceVersion.latestSupported()
    }
}
