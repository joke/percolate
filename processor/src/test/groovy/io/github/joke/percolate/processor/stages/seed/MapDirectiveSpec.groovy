package io.github.joke.percolate.processor.stages.seed

import io.github.joke.percolate.processor.model.MappingDirective
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class MapDirectiveSpec extends Specification {

    def 'exposes a present constant with no source path'() {
        when:
        def directive = MapDirective.from(mapping('status', null, 'ACTIVE', null))

        then:
        directive.constant().present
        directive.constant().get() == 'ACTIVE'
        directive.defaultValue().empty
        directive.sourcePath().empty
    }

    def 'reports an unspecified attribute as absent'() {
        when:
        def directive = MapDirective.from(mapping('x', 'in.x', null, null))

        then:
        directive.constant().empty
        directive.defaultValue().empty
        directive.sourcePath() == ['in', 'x']
    }

    def 'reports an empty-string attribute as present, not absent'() {
        when:
        def directive = MapDirective.from(mapping('note', null, '', null))

        then:
        directive.constant().present
        directive.constant().get() == ''
    }

    def 'splits a default directive source into segments and exposes the default'() {
        when:
        def directive = MapDirective.from(mapping('name', 'in.name', null, 'unknown'))

        then:
        directive.sourcePath() == ['in', 'name']
        directive.defaultValue().get() == 'unknown'
    }

    private static MappingDirective mapping(
            final String target, final String source, final String constant, final String defaultValue) {
        new MappingDirective(target, source, constant, defaultValue, null, null, null, null, null)
    }
}
