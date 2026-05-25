package io.github.joke.percolate.processor

import spock.lang.Specification
import spock.lang.Tag

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

    def 'PercolateProcessor advertises percolate.nullable.annotations option'() {
        when:
        def supported = new PercolateProcessor().supportedOptions

        then:
        'percolate.nullable.annotations' in supported
        'percolate.debug.graphs' in supported
    }
}
