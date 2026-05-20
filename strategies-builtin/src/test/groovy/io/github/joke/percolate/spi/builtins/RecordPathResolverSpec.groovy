package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class RecordPathResolverSpec extends Specification {

    def 'matches a canonical record accessor'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def point = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.Point').asType()

        when:
        def result = new RecordPathResolver().resolve(point, 'x', ctx)

        then:
        result.present
        result.get().returnType.kind.name() == 'INT'
        result.get().weight == Weights.STEP
    }

    def 'rejects plain-class parents that are not records'() {
        given:
        def ctx = new ResolveCtxBuilder().build()
        def personBean = TypeUniverse.element('io.github.joke.percolate.spi.builtins.fixtures.PersonBean').asType()

        when:
        def result = new RecordPathResolver().resolve(personBean, 'name', ctx)

        then:
        !result.present
    }
}
