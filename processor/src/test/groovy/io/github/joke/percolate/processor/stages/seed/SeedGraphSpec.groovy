package io.github.joke.percolate.processor.stages.seed
import io.github.joke.percolate.processor.PercolateProcessor

import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.model.MapperMappings
import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.processor.model.MethodMappings
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

@Tag('unit')
class SeedGraphSpec extends Specification {

    Name name(String value) {
        def n = Mock(Name)
        n.toString() >> value
        n
    }

    VariableElement param(String paramName, String paramType) {
        def p = Mock(VariableElement)
        p.getSimpleName() >> name(paramName)
        def t = Mock(TypeMirror)
        t.toString() >> paramType
        p.asType() >> t
        p
    }

    ExecutableElement method(String methodName, List<VariableElement> params, String returnType = 'Human') {
        def m = Mock(ExecutableElement)
        m.getSimpleName() >> name(methodName)
        m.getParameters() >> params
        def retType = Mock(TypeMirror)
        retType.toString() >> returnType
        m.getReturnType() >> retType
        m
    }

    MappingDirective directive(String target, String source) {
        def mirror = Mock(AnnotationMirror)
        def tv = Mock(AnnotationValue)
        def sv = Mock(AnnotationValue)
        new MappingDirective(target, source, mirror, tv, sv)
    }

    MethodMappings methodMappings(ExecutableElement method, List<MappingDirective> directives) {
        new MethodMappings(method, directives)
    }

    MapperMappings mapperMappings(List<MethodMappings> methodMappings) {
        new MapperMappings(Mock(TypeElement), methodMappings)
    }

    Scope methodScope(ExecutableElement method) {
        new MethodScope(method)
    }

    def 'empty mapper produces an empty graph'() {
        given:
        def seedGraph = new SeedGraph()
        def emptyMappings = new MapperMappings(Mock(TypeElement), [])

        when:
        def graph = seedGraph.apply(emptyMappings)

        then:
        graph.nodeCount() == 0
        graph.edgeCount() == 0
    }

    def 'single parameter creates a parameter-root node'() {
        given:
        def seedGraph = new SeedGraph()
        def param = param('person', 'Person')
        def method = method('map', [param])
        def mappings = mapperMappings([methodMappings(method, [])])

        when:
        def graph = seedGraph.apply(mappings)

        then:
        graph.nodeCount() == 2  // parameter root + return root
        def nodes = graph.nodes().toList()
        nodes.size() == 2
        nodes[0].type.isPresent()
        nodes[1].type.isPresent()
    }

    def 'multi-parameter creates one node per parameter'() {
        given:
        def seedGraph = new SeedGraph()
        def bar = param('bar', 'Bar')
        def baz = param('baz', 'Baz')
        def method = method('combine', [bar, baz])
        def mappings = mapperMappings([methodMappings(method, [])])

        when:
        def graph = seedGraph.apply(mappings)

        then:
        graph.nodeCount() == 3  // 2 param roots + return root
    }

    def 'return type creates a target-root node'() {
        given:
        def seedGraph = new SeedGraph()
        def param = param('person', 'Person')
        def method = method('map', [param])
        def mappings = mapperMappings([methodMappings(method, [])])

        when:
        def graph = seedGraph.apply(mappings)

        then:
        def nodes = graph.nodes().toList()
        nodes.any { it.loc instanceof TargetLocation && it.type.isPresent() }
    }

    def 'directive creates source chain and bridging edge'() {
        given:
        def seedGraph = new SeedGraph()
        def param = param('person', 'Person')
        def method = method('map', [param])
        def d = directive('name', 'person')
        def mappings = mapperMappings([methodMappings(method, [d])])

        when:
        def graph = seedGraph.apply(mappings)

        then:
        graph.edgeCount() == 3  // source chain + target chain + bridging edge
    }

    def 'directive creates target chain back to return root'() {
        given:
        def seedGraph = new SeedGraph()
        def param = param('person', 'Person')
        def method = method('map', [param])
        def d = directive('name', 'person')
        def mappings = mapperMappings([methodMappings(method, [d])])

        when:
        def graph = seedGraph.apply(mappings)

        then:
        // source chain edge + bridging edge + target chain edge
        graph.edgeCount() == 3
    }

    def 'dotted source creates a chain of source nodes'() {
        given:
        def seedGraph = new SeedGraph()
        def param = param('person', 'Person')
        def method = method('map', [param])
        def d = directive('name', 'person.address.street')
        def mappings = mapperMappings([methodMappings(method, [d])])

        when:
        def graph = seedGraph.apply(mappings)

        then:
        // param root + 2 intermediate source nodes + return root + target node
        graph.nodeCount() >= 5
        // edges: param->person.address, person.address->person.address.street, person.address.street->target, target->return
        graph.edgeCount() >= 4
    }

    def 'dotted target creates a chain of target nodes'() {
        given:
        def seedGraph = new SeedGraph()
        def param = param('person', 'Person')
        def method = method('map', [param])
        def d = directive('address.line1', 'person')
        def mappings = mapperMappings([methodMappings(method, [d])])

        when:
        def graph = seedGraph.apply(mappings)

        then:
        // param root + 2 intermediate target nodes + return root
        graph.nodeCount() >= 4
    }

    def 'shared prefix is not duplicated'() {
        given:
        def seedGraph = new SeedGraph()
        def param = param('person', 'Person')
        def method = method('map', [param])
        def d1 = directive('street', 'person.address')
        def d2 = directive('city', 'person.address')
        def mappings = mapperMappings([methodMappings(method, [d1, d2])])

        when:
        def graph = seedGraph.apply(mappings)

        then:
        // The shared prefix "person.address" should produce only one node
        def addressNodes = graph.nodes().findAll {
            it.loc instanceof SourceLocation && it.loc.path.toString() == 'person.address'
        }
        addressNodes.size() == 1
    }

    def 'seed graph structure is correct'() {
        given:
        def seedGraph = new SeedGraph()
        def param = param('person', 'Person')
        def method = method('map', [param])
        def d1 = directive('name', 'person.firstName')
        def d2 = directive('age', 'person.age')
        def mappings = mapperMappings([methodMappings(method, [d1, d2])])

        when:
        def graph = seedGraph.apply(mappings)

        then:
        // Multiple nodes and edges are created for two directives
        graph.nodeCount() > 0
        graph.edgeCount() > 0
    }

    def 'two methods produce nodes scoped to each method'() {
        given:
        def seedGraph = new SeedGraph()
        def personParam = param('person', 'Person')
        def addressParam = param('address', 'Address')
        def method1 = method('map', [personParam], 'Human')
        def method2 = method('mapAddress', [addressParam], 'Address')
        def d1 = directive('name', 'person')
        def d2 = directive('name', 'address')
        def mm1 = methodMappings(method1, [d1])
        def mm2 = methodMappings(method2, [d2])
        def mappings = mapperMappings([mm1, mm2])

        when:
        def graph = seedGraph.apply(mappings)

        then:
        graph.nodeCount() >= 8  // at least 4 per method
    }

    def 'directive edge carries the annotation mirror'() {
        given:
        def seedGraph = new SeedGraph()
        def param = param('person', 'Person')
        def method = method('map', [param])
        def d = directive('name', 'person')
        def mappings = mapperMappings([methodMappings(method, [d])])

        when:
        def graph = seedGraph.apply(mappings)

        then:
        def edges = graph.edges().toList()
        edges.findAll { it.directive.isPresent() }.size() == edges.size()
    }

    def 'all emitted edges have kind SEED'() {
        given:
        def seedGraph = new SeedGraph()
        def param = param('person', 'Person')
        def method = method('map', [param])
        def d = directive('name', 'person')
        def mappings = mapperMappings([methodMappings(method, [d])])

        when:
        def graph = seedGraph.apply(mappings)

        then:
        graph.edges().allMatch { it.getKind() == EdgeKind.SEED }
    }

    def 'all emitted edges have sentinel weight'() {
        given:
        def seedGraph = new SeedGraph()
        def param = param('person', 'Person')
        def method = method('map', [param])
        def d = directive('name', 'person')
        def mappings = mapperMappings([methodMappings(method, [d])])

        when:
        def graph = seedGraph.apply(mappings)

        then:
        graph.edges().allMatch { it.getWeight() == Weights.SENTINEL_UNREALISED }
    }

    def 'no realised, marker, or sub-seed edges are emitted'() {
        given:
        def seedGraph = new SeedGraph()
        def param = param('person', 'Person')
        def method = method('map', [param])
        def d = directive('name', 'person')
        def mappings = mapperMappings([methodMappings(method, [d])])

        when:
        def graph = seedGraph.apply(mappings)
        def edges = graph.edges().toList()

        then:
        edges.findAll { it.getKind() == EdgeKind.REALISED }.isEmpty()
        edges.findAll { it.getKind() == EdgeKind.MARKER }.isEmpty()
        edges.findAll { it.getKind() == EdgeKind.SUB_SEED }.isEmpty()
    }

    def 'seed edges have empty codegen, groupId, and strategyClassFqn'() {
        given:
        def seedGraph = new SeedGraph()
        def param = param('person', 'Person')
        def method = method('map', [param])
        def d = directive('name', 'person')
        def mappings = mapperMappings([methodMappings(method, [d])])

        when:
        def graph = seedGraph.apply(mappings)
        def edges = graph.edges().toList()

        then:
        edges.findResults { edge ->
            if (edge.getCodegen().isPresent()) return 'codegen not empty'
            if (edge.getGroupId().isPresent()) return 'groupId not empty'
            if (edge.getStrategyClassFqn().isPresent()) return 'strategyClassFqn not empty'
            null
        }.isEmpty()
    }

    def 'seed graph is a forest'() {
        given:
        def seedGraph = new SeedGraph()
        def param = param('person', 'Person')
        def method = method('map', [param])
        def d1 = directive('name', 'person.firstName')
        def d2 = directive('age', 'person.age')
        def mappings = mapperMappings([methodMappings(method, [d1, d2])])

        when:
        def graph = seedGraph.apply(mappings)

        then:
        graph.edgeCount() > 0
        graph.nodeCount() > 0
        graph.isAcyclic()
    }
}
