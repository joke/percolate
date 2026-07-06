package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.Location
import io.github.joke.percolate.processor.internal.graph.Scope
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.internal.graph.TargetPath
import io.github.joke.percolate.processor.internal.graph.Value
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Port
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

/**
 * {@link PortSourceResolver} unit-tested by mocking {@link SourceCandidates} and {@link OperationLander} — resolves
 * one port's feeding source by its declared {@link Port.Sourcing} mode (design D1 of change
 * {@code target-driven-engine}, decomposed by {@code decompose-engine-stages}).
 */
@Tag('unit')
class PortSourceResolverSpec extends Specification {

    SourceCandidates sourceCandidates = Mock()
    OperationLander operationLander = Mock()
    PortSourceResolver resolver = new PortSourceResolver(sourceCandidates, operationLander)

    Scope scope = Mock()
    Value output = Mock()
    TypeMirror portType = Mock()

    def 'a SUBTARGET port mints a fresh child-target demand at the child location, touching no collaborator'() {
        def port = Port.subTarget('addr', portType, Nullability.NON_NULL)

        when:
        def result = resolver.sourceForPort(output, 'root', port, null)

        then:
        1 * output.getScope() >> scope
        0 * sourceCandidates._
        0 * operationLander._

        expect:
        result == new AddValue(scope, Location.child('root', 'addr'), portType, Nullability.NON_NULL)
    }

    def 'a REUSE_OR_MINT port with a matching in-scope source reuses it'() {
        def port = new Port('src', portType, Nullability.NON_NULL)
        Value matched = Mock()
        AddValue reused = new AddValue(scope, Mock(Location), portType, Nullability.NON_NULL)

        when:
        def result = resolver.sourceForPort(output, 'root', port, null)

        then:
        1 * output.getScope() >> scope
        1 * sourceCandidates.matchingSource(scope, port, null) >> matched
        1 * operationLander.reuse(matched) >> reused
        0 * _

        expect:
        result.is(reused)
    }

    def 'a REUSE port with no in-scope source declines — the operation does not apply'() {
        def port = Port.reuse('src', portType, Nullability.NON_NULL)

        when:
        def result = resolver.sourceForPort(output, 'root', port, null)

        then:
        1 * output.getScope() >> scope
        1 * sourceCandidates.matchingSource(scope, port, null) >> null
        0 * operationLander._

        expect:
        result == null
    }

    def 'a REUSE_OR_MINT port with no in-scope source mints a fresh intermediate at the output location'() {
        def port = new Port('src', portType, Nullability.NON_NULL)
        def outputLoc = new TargetLocation(TargetPath.of(''))

        when:
        def result = resolver.sourceForPort(output, 'root', port, null)

        then:
        2 * output.getScope() >> scope
        1 * sourceCandidates.matchingSource(scope, port, null) >> null
        1 * output.loc >> outputLoc
        0 * operationLander._

        expect:
        result == new AddValue(scope, outputLoc, portType, Nullability.NON_NULL)
    }

    def 'a pinned source is passed through to SourceCandidates ranking'() {
        def port = new Port('src', portType, Nullability.NON_NULL)
        Value pinned = Mock()
        Value matched = Mock()
        AddValue reused = new AddValue(scope, Mock(Location), portType, Nullability.NON_NULL)

        when:
        def result = resolver.sourceForPort(output, 'root', port, pinned)

        then:
        1 * output.getScope() >> scope
        1 * sourceCandidates.matchingSource(scope, port, pinned) >> matched
        1 * operationLander.reuse(matched) >> reused
        0 * _

        expect:
        result.is(reused)
    }
}
