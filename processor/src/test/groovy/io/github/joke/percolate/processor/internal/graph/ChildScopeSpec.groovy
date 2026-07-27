package io.github.joke.percolate.processor.internal.graph

import spock.lang.Specification
import spock.lang.Tag

/**
 * {@link ChildScope#inputDecls} unit-tested mock-only: yields exactly its own element input declaration, set once
 * when the owning Operation lands (design D5 of change {@code decouple-engine-from-strategy-semantics}) — a
 * {@code ChildScope} no longer re-exports its parent's declarations; ancestor visibility is the selector's own
 * concern (see {@code SourceCandidates}), not a second stream on the scope.
 */
@Tag('unit')
class ChildScopeSpec extends Specification {

    Operation owner = Mock()
    Scope parentScope = Mock()
    ChildScope scope = new ChildScope(owner, parentScope)

    def 'inputDecls yields exactly the element input declaration set at initialise'() {
        InputDecl elementInput = Mock()
        Value returnRoot = Mock()
        scope.initialise(returnRoot, elementInput)

        expect:
        scope.inputDecls().toList() == [elementInput]
    }
}
