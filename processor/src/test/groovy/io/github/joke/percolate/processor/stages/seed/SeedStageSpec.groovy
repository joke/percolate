package io.github.joke.percolate.processor.stages.seed

import io.github.joke.percolate.processor.graph.EdgeKind
import io.github.joke.percolate.processor.graph.SourceLocation
import io.github.joke.percolate.processor.graph.TargetLocation
import io.github.joke.percolate.processor.model.MapperMappings
import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.processor.model.MethodMappings
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

@Tag('unit')
class SeedStageSpec extends Specification {

    def 'every edge emitted is SEED with empty codegen and empty strategyClassFqn'() {
        given:
        def method = mockMethod('m', [param('person', TypeUniverse.element('java.lang.Object').asType())], TypeUniverse.STRING)
        def directive = directive('lastName', 'person.lastName')
        def mappings = mappings(method, [directive])

        when:
        def graph = new SeedStage().apply(mappings)

        then:
        graph.edges().toList().every { it.kind == EdgeKind.SEED && it.codegen.empty && it.strategyClassFqn.empty }
    }

    def 'every SEED edge has exactly one corresponding ExpansionGroup'() {
        given:
        def method = mockMethod('m', [param('person', TypeUniverse.element('java.lang.Object').asType())], TypeUniverse.STRING)
        def directive = directive('lastName', 'person.lastName')
        def mappings = mappings(method, [directive])

        when:
        def graph = new SeedStage().apply(mappings)
        def edges = graph.edges().toList()
        def groups = graph.groups().toList()

        then:
        groups.size() == edges.size()
        edges.every { edge ->
            groups.count { it.root.is(edge.to) && it.inputs().size() == 1 && it.inputs()[0].is(edge.from) } == 1
        }
    }

    def 'path-segment edge produces a path-segment group with untyped root'() {
        given:
        def personType = TypeUniverse.element('java.lang.Object').asType()
        def method = mockMethod('m', [param('person', personType)], TypeUniverse.STRING)
        def directive = directive('lastName', 'person.lastName')
        def mappings = mappings(method, [directive])

        when:
        def graph = new SeedStage().apply(mappings)

        then: 'one path-segment SEED edge: src[person]:Person → src[person.lastName]:?'
        def pathEdges = graph.edges()
                .filter { it.from.loc instanceof SourceLocation && it.to.loc instanceof SourceLocation }
                .toList()
        pathEdges.size() == 1
        pathEdges[0].from.loc.path.segments == ['person']
        pathEdges[0].from.type.present
        pathEdges[0].to.loc.path.segments == ['person', 'lastName']
        pathEdges[0].to.type.empty

        and: 'matching ExpansionGroup with root = src[person.lastName] and slot = src[person]'
        def groups = graph.groups().filter {
            it.root.loc instanceof SourceLocation && it.root.loc.path.segments == ['person', 'lastName']
        }.toList()
        groups.size() == 1
        groups[0].inputs().size() == 1
        groups[0].inputs()[0].loc.path.segments == ['person']
    }

    def 'directive-bridging edge produces a directive-binding group; from is untyped seed leaf'() {
        given:
        def personType = TypeUniverse.element('java.lang.Object').asType()
        def method = mockMethod('m', [param('person', personType)], TypeUniverse.STRING)
        def directive = directive('lastName', 'person.lastName')
        def mappings = mappings(method, [directive])

        when:
        def graph = new SeedStage().apply(mappings)

        then:
        def bridges = graph.edges()
                .filter { it.from.loc instanceof SourceLocation && it.to.loc instanceof TargetLocation }
                .toList()
        bridges.size() == 1
        bridges[0].from.type.empty
        bridges[0].from.loc.path.segments == ['person', 'lastName']
        bridges[0].to.loc.path.segments == ['lastName']

        and:
        def groups = graph.groups().filter {
            it.root.loc instanceof TargetLocation && it.root.loc.path.segments == ['lastName']
        }.toList()
        groups.size() == 1
        groups[0].inputs()[0].loc.path.segments == ['person', 'lastName']
        groups[0].inputs()[0].type.empty
    }

    def 'target-chain edge produces a target-chain group'() {
        given:
        def method = mockMethod('m', [param('p', TypeUniverse.element('java.lang.Object').asType())], TypeUniverse.STRING)
        def directive = directive('addresses', 'p')
        def mappings = mappings(method, [directive])

        when:
        def graph = new SeedStage().apply(mappings)

        then: 'one target-chain SEED edge tgt[addresses]:? → tgt[]:String'
        def chainEdges = graph.edges()
                .filter { it.from.loc instanceof TargetLocation && it.to.loc instanceof TargetLocation }
                .toList()
        chainEdges.size() == 1
        chainEdges[0].from.loc.path.segments == ['addresses']
        chainEdges[0].to.loc.path.segments == []

        and:
        def groups = graph.groups().filter {
            it.root.loc instanceof TargetLocation && it.root.loc.path.segments == []
        }.toList()
        groups.size() == 1
        groups[0].inputs()[0].loc.path.segments == ['addresses']
    }

    def 'two directives sharing a source prefix share the intermediate node and group is registered once'() {
        given:
        def personType = TypeUniverse.element('java.lang.Object').asType()
        def method = mockMethod('m', [param('person', personType)], TypeUniverse.STRING)
        def directives = [directive('a', 'person.address.street'), directive('b', 'person.address.city')]
        def mappings = mappings(method, directives)

        when:
        def graph = new SeedStage().apply(mappings)

        then: 'exactly one untyped src[person.address] node'
        graph.nodes()
                .filter { it.loc instanceof SourceLocation && it.loc.path.segments == ['person', 'address'] }
                .toList()
                .size() == 1

        and: 'path-segment groups: person->address (once), address->street, address->city = 3'
        def srcGroups = graph.groups()
                .filter { it.root.loc instanceof SourceLocation }
                .toList()
        srcGroups.size() == 3
        srcGroups.count { it.root.loc.path.segments == ['person', 'address'] } == 1
        srcGroups.count { it.root.loc.path.segments == ['person', 'address', 'street'] } == 1
        srcGroups.count { it.root.loc.path.segments == ['person', 'address', 'city'] } == 1
    }

    def 'single-segment source matching parameter uses paramRoot directly; no path-segment group'() {
        given:
        def personType = TypeUniverse.element('java.lang.Object').asType()
        def method = mockMethod('m', [param('person', personType)], TypeUniverse.STRING)
        def directive = directive('x', 'person')
        def mappings = mappings(method, [directive])

        when:
        def graph = new SeedStage().apply(mappings)

        then: 'directive bridging edge starts at the typed paramRoot src[person]:Person'
        def bridges = graph.edges()
                .filter { it.from.loc instanceof SourceLocation && it.to.loc instanceof TargetLocation }
                .toList()
        bridges.size() == 1
        bridges[0].from.loc.path.segments == ['person']
        bridges[0].from.type.present
        bridges[0].to.loc.path.segments == ['x']

        and: 'no SourceLocation→SourceLocation path-segment edge or group is registered'
        graph.edges().filter { it.from.loc instanceof SourceLocation && it.to.loc instanceof SourceLocation }.toList().empty
        graph.groups().filter { it.root.loc instanceof SourceLocation }.toList().empty
    }

    def 'empty mapper produces an empty graph'() {
        given:
        def mappings = new MapperMappings(null, [])

        when:
        def graph = new SeedStage().apply(mappings)

        then:
        graph.nodeCount() == 0
        graph.edgeCount() == 0
        graph.groups().toList().empty
    }

    private VariableElement param(final String name, final TypeMirror type) {
        def p = Mock(VariableElement)
        p.simpleName >> nameOf(name)
        p.asType() >> type
        p
    }

    private ExecutableElement mockMethod(
            final String name, final List<VariableElement> params, final TypeMirror returnType) {
        def m = Mock(ExecutableElement)
        m.simpleName >> nameOf(name)
        m.parameters >> params
        m.returnType >> returnType
        m
    }

    private Name nameOf(final String value) {
        def n = Mock(Name)
        n.toString() >> value
        n
    }

    private MappingDirective directive(final String target, final String source) {
        new MappingDirective(target, source, Mock(AnnotationMirror), null, null)
    }

    private MapperMappings mappings(final ExecutableElement method, final List<MappingDirective> directives) {
        new MapperMappings(null, [new MethodMappings(method, directives)])
    }
}
