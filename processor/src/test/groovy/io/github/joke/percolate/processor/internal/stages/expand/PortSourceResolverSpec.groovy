package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.Location
import io.github.joke.percolate.processor.internal.graph.Scope
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.internal.graph.TargetPath
import io.github.joke.percolate.processor.internal.graph.Value
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.Subject
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

/**
 * {@link PortSourceResolver} unit-tested by mocking {@link SourceCandidates} and {@link OperationLander} — resolves
 * one port's feeding source by its declared axes (design D5 of change
 * {@code decouple-engine-from-strategy-semantics}): a sub-target port mints a child demand; otherwise the port's
 * selector picks the lookup and its on-miss rule decides what a miss means, including recording a {@code REQUIRE}
 * refusal on the demanded output.
 */
@Tag('unit')
class PortSourceResolverSpec extends Specification {

    SourceCandidates sourceCandidates = Mock()
    OperationLander operationLander = Mock()
    PortSourceResolver resolver = new PortSourceResolver(sourceCandidates, operationLander)

    Scope scope = Mock()
    Value output = Mock()
    TypeMirror portType = Mock()
    Subject subject = Mock()

    def 'a sub-target port mints a fresh child-target demand at the child location, touching no collaborator'() {
        def port = Port.subTarget('addr', portType, Nullability.NON_NULL)

        when:
        def result = resolver.sourceForPort(output, 'root', port, null, subject)

        then:
        1 * output.getScope() >> scope
        0 * sourceCandidates._
        0 * operationLander._

        expect:
        result == new AddValue(scope, Location.child('root', 'addr'), portType, Nullability.NON_NULL)
    }

    def 'a BY_TYPE/MINT port with a matching in-scope source reuses it'() {
        def port = new Port('src', portType, Nullability.NON_NULL)
        Value matched = Mock()
        AddValue reused = new AddValue(scope, Mock(Location), portType, Nullability.NON_NULL)

        when:
        def result = resolver.sourceForPort(output, 'root', port, null, subject)

        then:
        1 * output.getScope() >> scope
        1 * sourceCandidates.matchingSource(scope, port, null) >> matched
        1 * operationLander.reuse(matched) >> reused
        0 * _

        expect:
        result.is(reused)
    }

    def 'a BY_TYPE/DECLINE port with no in-scope source declines — the operation does not apply'() {
        def port = Port.byTypeOrDecline('src', portType, Nullability.NON_NULL)

        when:
        def result = resolver.sourceForPort(output, 'root', port, null, subject)

        then:
        1 * output.getScope() >> scope
        1 * sourceCandidates.matchingSource(scope, port, null) >> null
        0 * operationLander._
        0 * output.addInadmissible(_)

        expect:
        result == null
    }

    def 'a BY_TYPE/MINT port with no in-scope source mints a fresh intermediate at the output location'() {
        def port = new Port('src', portType, Nullability.NON_NULL)
        def outputLoc = new TargetLocation(TargetPath.of(''))

        when:
        def result = resolver.sourceForPort(output, 'root', port, null, subject)

        then:
        2 * output.getScope() >> scope
        1 * sourceCandidates.matchingSource(scope, port, null) >> null
        1 * output.loc >> outputLoc
        0 * operationLander._

        expect:
        result == new AddValue(scope, outputLoc, portType, Nullability.NON_NULL)
    }

    def 'a BY_NAME/REQUIRE port with a resolved binding reuses it, touching no BY_TYPE path'() {
        def port = Port.byName('order', portType, Nullability.NON_NULL, 'order')
        Value bound = Mock()
        AddValue reused = new AddValue(scope, Mock(Location), portType, Nullability.NON_NULL)

        when:
        def result = resolver.sourceForPort(output, 'root', port, null, subject)

        then:
        1 * output.getScope() >> scope
        1 * sourceCandidates.byNameSource(scope, port) >> bound
        1 * operationLander.reuse(bound) >> reused
        0 * _

        expect:
        result.is(reused)
    }

    def 'a BY_NAME/REQUIRE port with no resolvable binding records a refusal naming the unbound binding name'() {
        def port = Port.byName('order', portType, Nullability.NON_NULL, 'order')

        when:
        def result = resolver.sourceForPort(output, 'root', port, null, subject)

        then:
        2 * output.getScope() >> scope
        1 * sourceCandidates.byNameSource(scope, port) >> null
        1 * sourceCandidates.byNameDeclaredType(scope, port) >> Optional.empty()
        1 * output.addInadmissible {
            it.subject.is(subject) && it.message.contains("port 'order'") && it.message.contains("'order'")
        }
        0 * operationLander._

        expect:
        result == null
    }

    def 'a BY_NAME/REQUIRE port with a mismatched binding type records a refusal naming both types'() {
        def port = Port.byName('order', portType, Nullability.NON_NULL, 'order')
        TypeMirror declaredType = Mock()

        when:
        def result = resolver.sourceForPort(output, 'root', port, null, subject)

        then:
        2 * output.getScope() >> scope
        1 * sourceCandidates.byNameSource(scope, port) >> null
        1 * sourceCandidates.byNameDeclaredType(scope, port) >> Optional.of(declaredType)
        1 * output.addInadmissible {
            it.subject.is(subject) && it.message.contains(declaredType.toString()) && it.message.contains(portType.toString())
        }
        0 * operationLander._

        expect:
        result == null
    }

    def 'a pinned source is passed through to SourceCandidates ranking'() {
        def port = new Port('src', portType, Nullability.NON_NULL)
        Value pinned = Mock()
        Value matched = Mock()
        AddValue reused = new AddValue(scope, Mock(Location), portType, Nullability.NON_NULL)

        when:
        def result = resolver.sourceForPort(output, 'root', port, pinned, subject)

        then:
        1 * output.getScope() >> scope
        1 * sourceCandidates.matchingSource(scope, port, pinned) >> matched
        1 * operationLander.reuse(matched) >> reused
        0 * _

        expect:
        result.is(reused)
    }
}
