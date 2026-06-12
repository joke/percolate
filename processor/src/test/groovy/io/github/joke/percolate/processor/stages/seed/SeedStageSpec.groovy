package io.github.joke.percolate.processor.stages.seed

import io.github.joke.percolate.processor.graph.ConstantLocation
import io.github.joke.percolate.processor.graph.EdgeKind
import io.github.joke.percolate.processor.graph.SourceLocation
import io.github.joke.percolate.processor.graph.TargetLocation
import io.github.joke.percolate.processor.model.MapperMappings
import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.processor.model.MethodMappings
import io.github.joke.percolate.spi.test.TypeUniverse
import org.jgrapht.alg.cycle.CycleDetector
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
            groups.count { it.root.is(graph.getEdgeTarget(edge)) && it.inputs().size() == 1 && it.inputs()[0].is(graph.getEdgeSource(edge)) } == 1
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
                .filter { graph.getEdgeSource(it).loc instanceof SourceLocation && graph.getEdgeTarget(it).loc instanceof SourceLocation }
                .toList()
        pathEdges.size() == 1
        graph.getEdgeSource(pathEdges[0]).loc.path.segments == ['person']
        graph.getEdgeSource(pathEdges[0]).type.present
        graph.getEdgeTarget(pathEdges[0]).loc.path.segments == ['person', 'lastName']
        graph.getEdgeTarget(pathEdges[0]).type.empty

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
                .filter { graph.getEdgeSource(it).loc instanceof SourceLocation && graph.getEdgeTarget(it).loc instanceof TargetLocation }
                .toList()
        bridges.size() == 1
        graph.getEdgeSource(bridges[0]).type.empty
        graph.getEdgeSource(bridges[0]).loc.path.segments == ['person', 'lastName']
        graph.getEdgeTarget(bridges[0]).loc.path.segments == ['lastName']

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
                .filter { graph.getEdgeSource(it).loc instanceof TargetLocation && graph.getEdgeTarget(it).loc instanceof TargetLocation }
                .toList()
        chainEdges.size() == 1
        graph.getEdgeSource(chainEdges[0]).loc.path.segments == ['addresses']
        graph.getEdgeTarget(chainEdges[0]).loc.path.segments == []

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
                .filter { graph.getEdgeSource(it).loc instanceof SourceLocation && graph.getEdgeTarget(it).loc instanceof TargetLocation }
                .toList()
        bridges.size() == 1
        graph.getEdgeSource(bridges[0]).loc.path.segments == ['person']
        graph.getEdgeSource(bridges[0]).type.present
        graph.getEdgeTarget(bridges[0]).loc.path.segments == ['x']

        and: 'no SourceLocation→SourceLocation path-segment edge or group is registered'
        graph.edges().filter { graph.getEdgeSource(it).loc instanceof SourceLocation && graph.getEdgeTarget(it).loc instanceof SourceLocation }.toList().empty
        graph.groups().filter { it.root.loc instanceof SourceLocation }.toList().empty
    }

    def 'a constant directive seeds an untyped constant-value node bridged to the target with a directive-binding demand'() {
        given:
        def method = mockMethod('m', [param('person', TypeUniverse.element('java.lang.Object').asType())], TypeUniverse.STRING)
        def mappings = mappings(method, [constantDirective('status', 'ACTIVE')])

        when:
        def graph = new SeedStage().apply(mappings)

        then: 'one untyped constant-value node carrying the raw literal'
        def constNodes = graph.nodes().filter { it.loc instanceof ConstantLocation }.toList()
        constNodes.size() == 1
        constNodes[0].loc.raw == 'ACTIVE'
        constNodes[0].type.empty

        and: 'a bridging SEED edge from the constant node to tgt[status]'
        def bridges = graph.edges().filter { graph.getEdgeSource(it).loc instanceof ConstantLocation }.toList()
        bridges.size() == 1
        bridges[0].kind == EdgeKind.SEED
        graph.getEdgeTarget(bridges[0]).loc instanceof TargetLocation
        graph.getEdgeTarget(bridges[0]).loc.path.segments == ['status']

        and: 'a directive-binding demand root=tgt[status], single input = the constant node'
        def groups = graph.groups()
                .filter { it.root.loc instanceof TargetLocation && it.root.loc.path.segments == ['status'] }
                .toList()
        groups.size() == 1
        groups[0].inputs().size() == 1
        groups[0].inputs()[0].loc instanceof ConstantLocation

        and: 'no source node (only the parameter root exists)'
        graph.nodes().filter { it.loc instanceof SourceLocation }.collect { it.loc.path.segments } == [['person']]
    }

    def 'a constant directive still seeds its target chain to the root'() {
        given:
        def method = mockMethod('m', [param('p', TypeUniverse.element('java.lang.Object').asType())], TypeUniverse.STRING)
        def mappings = mappings(method, [constantDirective('address.zip', '00000')])

        when:
        def graph = new SeedStage().apply(mappings)

        then: 'target nodes for [address] and [address,zip] chained out to the return root'
        def targetPaths = graph.nodes().filter { it.loc instanceof TargetLocation }.collect { it.loc.path.segments }
        targetPaths.contains(['address'])
        targetPaths.contains(['address', 'zip'])
        targetPaths.contains([])

        and: 'the bridge targets the deepest target node'
        def bridges = graph.edges().filter { graph.getEdgeSource(it).loc instanceof ConstantLocation }.toList()
        graph.getEdgeTarget(bridges[0]).loc.path.segments == ['address', 'zip']
    }

    def 'a seed graph containing a constant remains acyclic'() {
        given:
        def method = mockMethod('m', [param('p', TypeUniverse.element('java.lang.Object').asType())], TypeUniverse.STRING)
        def mappings = mappings(method, [constantDirective('status', 'ACTIVE')])

        when:
        def graph = new SeedStage().apply(mappings)

        then:
        !new CycleDetector<>(graph.underlyingGraph()).detectCycles()
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
        new MappingDirective(target, source, null, null, Mock(AnnotationMirror), null, null, null, null)
    }

    private MappingDirective constantDirective(final String target, final String constant) {
        new MappingDirective(target, null, constant, null, Mock(AnnotationMirror), null, null, null, null)
    }

    private MapperMappings mappings(final ExecutableElement method, final List<MappingDirective> directives) {
        new MapperMappings(null, [new MethodMappings(method, directives)])
    }
}
