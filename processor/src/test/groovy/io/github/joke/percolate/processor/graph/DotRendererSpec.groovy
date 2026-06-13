package io.github.joke.percolate.processor.graph

import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class DotRendererSpec extends Specification {

    final MapperGraph graph = new MapperGraph()
    final Scope scope = new HarnessScope('m()')
    final DotRenderer renderer = new DotRenderer()

    def 'renders Operations as boxes, Values as ellipses, and labels edges by port'() {
        given:
        graph.apply(new AddOperation('new Address', 'test.Strategy', Stub(Codegen), 1,
                [port('number', TypeUniverse.INT), port('street', TypeUniverse.STRING)],
                target('addr', TypeUniverse.STRING), Optional.empty()))

        when:
        def dot = renderer.render(graph.bipartiteView(), scope.encode())

        then: 'the Operation is a box carrying its label; Values are ellipses; ports label the edges'
        dot.contains('shape="box"')
        dot.contains('shape="ellipse"')
        dot.contains('new Address')
        dot.contains('label="number"')
        dot.contains('label="street"')
        dot.contains(scope.encode())
    }

    private AddValue leaf(final String slot, final TypeMirror type) {
        new AddValue(scope, new SourceLocation(AccessPath.of(slot)), type, Nullability.NON_NULL)
    }

    private AddValue target(final String slot, final TypeMirror type) {
        new AddValue(scope, new TargetLocation(TargetPath.of(slot)), type, Nullability.NON_NULL)
    }

    private PortBinding port(final String name, final TypeMirror type) {
        new PortBinding(new Port(name, type, Nullability.NON_NULL), leaf(name, type))
    }
}
