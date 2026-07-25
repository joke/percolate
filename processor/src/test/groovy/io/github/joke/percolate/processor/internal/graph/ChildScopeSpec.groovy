package io.github.joke.percolate.processor.internal.graph

import spock.lang.Specification
import spock.lang.Tag

import java.util.stream.Stream

/**
 * {@link ChildScope#ambientDecls} unit-tested mock-only: inherits its parent scope's ambient environment
 * unchanged, exactly like {@link ChildScope#inputDecls} inherits nothing but delegates for ambients (design
 * Decision 5).
 */
@Tag('unit')
class ChildScopeSpec extends Specification {

    Operation owner = Mock()
    Scope parentScope = Mock()
    ChildScope scope = new ChildScope(owner, parentScope)

    def 'ambientDecls delegates to the parent scope unchanged'() {
        AmbientDecl inherited = Mock()
        def nullness = { t, e -> null }

        when:
        def decls = scope.ambientDecls(nullness).toList()

        then:
        1 * parentScope.ambientDecls(_) >> Stream.of(inherited)
        0 * _

        expect:
        decls == [inherited]
    }
}
