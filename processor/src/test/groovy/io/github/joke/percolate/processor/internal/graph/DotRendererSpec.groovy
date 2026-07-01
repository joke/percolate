package io.github.joke.percolate.processor.internal.graph

import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Isolated
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
@Isolated // bridge: shares the static TypeUniverse javac; serialise until the type-universe redesign (see openspec/notes.md)
class DotRendererSpec extends Specification {

    final MapperGraph graph = new MapperGraph()
    final Scope scope = new HarnessScope('m()')
    final DotRenderer renderer = new DotRenderer()

    def 'renders Operations as boxes, Values as ellipses, and labels edges by port'() {
        given:
        graph.apply(new AddOperation('new Address', Stub(Codegen), 1, false,
                [port('number', TypeUniverse.INT), port('street', TypeUniverse.STRING)],
                target('addr', TypeUniverse.STRING), Optional.empty()))

        when:
        def dot = renderer.render(graph.bipartiteView(), scope.encode()) { false }

        then: 'the Operation is a box carrying its typed label; Values are ellipses; ports label the edges'
        dot.contains('shape="box"')
        dot.contains('shape="ellipse"')
        dot.contains('new Address (1)')
        dot.contains('label="number"')
        dot.contains('label="street"')
        dot.contains(scope.encode())
        !dot.contains('$$Lambda')
    }

    def 'value types render simple names with a JSpecify nullness mark, no package qualifiers'() {
        given: 'a non-null String target and a nullable String source'
        graph.apply(new AddOperation('assign', Stub(Codegen), 0, false,
                [new PortBinding(new Port('value', TypeUniverse.STRING, Nullability.NULLABLE),
                        new AddValue(scope, new SourceLocation(AccessPath.of('s')), TypeUniverse.STRING, Nullability.NULLABLE))],
                target('name', TypeUniverse.STRING), Optional.empty()))

        when:
        def dot = renderer.render(graph.bipartiteView(), scope.encode()) { false }

        then: 'the non-null target reads String! and the nullable source reads String?, never the FQN in the label'
        dot.contains('String!')
        dot.contains('String?')
        !dot.contains('java.lang.String!')
        !dot.contains('java.lang.String?')
    }

    def 'unreachable vertices are dimmed (grey, dashed) while reachable ones are not'() {
        given:
        graph.apply(new AddOperation('new Address', Stub(Codegen), 1, false,
                [port('street', TypeUniverse.STRING)], target('addr', TypeUniverse.STRING), Optional.empty()))

        when: 'every Operation is marked unreachable'
        def dot = renderer.render(graph.bipartiteView(), scope.encode()) { it instanceof Operation }

        then: 'the dimmed Operation box is grey and dashed; the un-dimmed Value keeps its fill'
        dot.contains('style="filled,dashed"')
        dot.contains('fillcolor="#DDDDDD"')
        dot.contains('#D7F0D0')
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
