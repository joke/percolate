package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

import java.lang.reflect.Modifier

/**
 * Structural guarantee of design D4: the {@link Frontier} a strategy receives is a myopic decision context. It
 * exposes only the type to produce, the in-effect directive, and a flat candidate snapshot — never the graph, a
 * group, or a node it could traverse. A {@link Candidate} likewise yields only a type, not a handle.
 */
@Tag('unit')
class FrontierSpec extends Specification {

    def 'Frontier exposes exactly the three myopic accessors and no graph handle'() {
        when:
        def methods = Frontier.declaredMethods.findAll { !it.synthetic && !Modifier.isStatic(it.modifiers) }

        then:
        methods*.name as Set == ['targetType', 'directive', 'candidates'] as Set

        and: 'their return types are plain value types — nothing from which the graph can be reached'
        methods.collect { it.returnType.name } as Set == [
                'javax.lang.model.type.TypeMirror',
                'java.util.Optional',
                'java.util.List',
        ] as Set
    }

    def 'Candidate exposes only its type, not a traversable handle'() {
        expect:
        Candidate.declaredMethods
                .findAll { !it.synthetic && it.name.startsWith('get') }*.name as Set == ['getType'] as Set
        Candidate.getDeclaredMethod('getType').returnType == javax.lang.model.type.TypeMirror
    }
}
