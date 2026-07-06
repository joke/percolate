package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.Location
import io.github.joke.percolate.processor.internal.graph.PortBinding
import io.github.joke.percolate.processor.internal.graph.Scope
import io.github.joke.percolate.processor.internal.graph.Value
import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationSpec
import io.github.joke.percolate.spi.Port
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

/**
 * {@link PortBinder} unit-tested by mocking {@link PortSourceResolver} — binds every port of a spec, or declines the
 * moment any one port cannot be sourced (design D1 of change {@code target-driven-engine}, decomposed by
 * {@code decompose-engine-stages}).
 */
@Tag('unit')
class PortBinderSpec extends Specification {

    PortSourceResolver portSourceResolver = Mock()
    PortBinder binder = new PortBinder(portSourceResolver)

    Value output = Mock()
    Codegen codegen = Mock()
    TypeMirror type = Mock()

    def 'binds every port of a spec that all resolve a source'() {
        def port0 = Port.reuse('a', type, Nullability.NON_NULL)
        def port1 = Port.reuse('b', type, Nullability.NON_NULL)
        def spec = OperationSpec.of('zip', codegen, 1, [port0, port1], type, Nullability.NON_NULL)
        def source0 = new AddValue(Mock(Scope), Mock(Location), type, Nullability.NON_NULL)
        def source1 = new AddValue(Mock(Scope), Mock(Location), type, Nullability.NON_NULL)

        when:
        def result = binder.bind(output, 'root', spec, null)

        then:
        1 * portSourceResolver.sourceForPort(output, 'root', port0, null) >> source0
        1 * portSourceResolver.sourceForPort(output, 'root', port1, null) >> source1
        0 * _

        expect:
        result.get() == [new PortBinding(port0, source0), new PortBinding(port1, source1)]
    }

    def 'declines the moment a port resolves no source, never checking the rest'() {
        def port0 = Port.reuse('a', type, Nullability.NON_NULL)
        def port1 = Port.reuse('b', type, Nullability.NON_NULL)
        def spec = OperationSpec.of('zip', codegen, 1, [port0, port1], type, Nullability.NON_NULL)

        when:
        def result = binder.bind(output, 'root', spec, null)

        then:
        1 * portSourceResolver.sourceForPort(output, 'root', port0, null) >> null
        0 * _

        expect:
        result.empty
    }

    def 'passes the pinned source and parent path through to every port'() {
        Value pinned = Mock()
        def port = Port.reuse('a', type, Nullability.NON_NULL)
        def spec = OperationSpec.of('copy', codegen, 1, [port], type, Nullability.NON_NULL)
        def source = new AddValue(Mock(Scope), Mock(Location), type, Nullability.NON_NULL)

        when:
        def result = binder.bind(output, 'parent.path', spec, pinned)

        then:
        1 * portSourceResolver.sourceForPort(output, 'parent.path', port, pinned) >> source
        0 * _

        expect:
        result.get() == [new PortBinding(port, source)]
    }

    def 'a spec with no ports binds trivially, touching no collaborator'() {
        def spec = OperationSpec.of('const', codegen, 1, [], type, Nullability.NON_NULL)

        when:
        def result = binder.bind(output, 'root', spec, null)

        then:
        0 * _

        expect:
        result.get().empty
    }
}
