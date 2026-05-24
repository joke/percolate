package io.github.joke.percolate.processor.stages.generate

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.model.MapperShape
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.GroupCodegen
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

@Tag('unit')
class BuildMethodBodiesSpec extends Specification {

    static final EdgeCodegen GETTER_FIRST_NAME = { vars, inputs ->
        CodeBlock.of('$L.getFirstName()', inputs.single())
    } as EdgeCodegen
    static final EdgeCodegen GETTER_LAST_NAME = { vars, inputs ->
        CodeBlock.of('$L.getLastName()', inputs.single())
    } as EdgeCodegen
    static final EdgeCodegen DIRECT_ASSIGN = { vars, inputs ->
        CodeBlock.of('$L', inputs.single())
    } as EdgeCodegen

    def 'build returns empty list when shape is null'() {
        expect:
        new BuildMethodBodies().build(new MapperContext(null)).empty
    }

    def 'leaf parameter renders by parameter name'() {
        given:
        def method = mockMethod('map', [mockParam('person')], TypeUniverse.STRING)
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def param = node(scope, sourceLoc('person'), TypeUniverse.STRING)
        def returnRoot = node(scope, returnRootLoc(), TypeUniverse.STRING)
        graph.addNode(param)
        graph.addNode(returnRoot)
        graph.addEdge(Edge.realised(param, returnRoot, Weights.NOOP, DIRECT_ASSIGN, 'DirectAssign'))
        def ctx = ctxWith(graph, method)

        when:
        def bodies = new BuildMethodBodies().build(ctx)

        then:
        bodies.size() == 1
        bodies[0].body.toString().trim() == 'return person;'
    }

    def 'DirectAssign over a single-segment getter renders person.getFirstName()'() {
        given:
        def method = mockMethod('map', [mockParam('person')], TypeUniverse.STRING)
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def param = node(scope, sourceLoc('person'), TypeUniverse.STRING)
        def field = node(scope, sourceLoc('person', 'firstName'), TypeUniverse.STRING)
        def returnRoot = node(scope, returnRootLoc(), TypeUniverse.STRING)
        graph.addNode(param)
        graph.addNode(field)
        graph.addNode(returnRoot)
        graph.addEdge(Edge.realised(param, field, Weights.STEP_GETTER, GETTER_FIRST_NAME, 'GetterPathResolver'))
        graph.addEdge(Edge.realised(field, returnRoot, Weights.NOOP, DIRECT_ASSIGN, 'DirectAssign'))
        def ctx = ctxWith(graph, method)

        when:
        def bodies = new BuildMethodBodies().build(ctx)

        then:
        bodies.size() == 1
        bodies[0].body.toString().trim() == 'return person.getFirstName();'
    }

    def 'ConstructorCall group assembles slot CodeBlocks by name'() {
        given:
        def method = mockMethod('map', [mockParam('person')], TypeUniverse.STRING)
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def param = node(scope, sourceLoc('person'), TypeUniverse.STRING)
        def firstNameSrc = node(scope, sourceLoc('person', 'firstName'), TypeUniverse.STRING)
        def lastNameSrc = node(scope, sourceLoc('person', 'lastName'), TypeUniverse.STRING)
        def firstNameSlot = node(scope, targetLoc('firstName'), TypeUniverse.STRING)
        def lastNameSlot = node(scope, targetLoc('lastName'), TypeUniverse.STRING)
        def returnRoot = node(scope, returnRootLoc(), TypeUniverse.STRING)

        [param, firstNameSrc, lastNameSrc, firstNameSlot, lastNameSlot, returnRoot].each { graph.addNode(it) }

        graph.addEdge(Edge.realised(param, firstNameSrc, Weights.STEP_GETTER, GETTER_FIRST_NAME, 'GetterPathResolver'))
        graph.addEdge(Edge.realised(param, lastNameSrc, Weights.STEP_GETTER, GETTER_LAST_NAME, 'GetterPathResolver'))
        graph.addEdge(Edge.realised(firstNameSrc, firstNameSlot, Weights.NOOP, DIRECT_ASSIGN, 'DirectAssign'))
        graph.addEdge(Edge.realised(lastNameSrc, lastNameSlot, Weights.NOOP, DIRECT_ASSIGN, 'DirectAssign'))

        GroupCodegen ctorCodegen = { vars, inputs ->
            CodeBlock.of('new Human($L, $L)', inputs.byName('firstName'), inputs.byName('lastName'))
        } as GroupCodegen
        def passThrough = { vars, inputs -> CodeBlock.of('$L', inputs.single()) } as EdgeCodegen
        def firstEdge = Edge.realised(firstNameSlot, returnRoot, Weights.STEP, passThrough, 'io.github.joke.percolate.spi.builtins.ConstructorCall')
        def lastEdge = Edge.realised(lastNameSlot, returnRoot, Weights.STEP, passThrough, 'io.github.joke.percolate.spi.builtins.ConstructorCall')
        graph.addEdge(firstEdge)
        graph.addEdge(lastEdge)
        graph.addGroup(ExpansionGroup.of(
                returnRoot,
                [firstNameSlot, lastNameSlot],
                ctorCodegen,
                'io.github.joke.percolate.spi.builtins.ConstructorCall',
                [firstEdge, lastEdge] as Set,
                graph))

        def ctx = ctxWith(graph, method)

        when:
        def bodies = new BuildMethodBodies().build(ctx)

        then:
        bodies.size() == 1
        bodies[0].body.toString().trim() == 'return new Human(person.getFirstName(), person.getLastName());'
    }

    private MapperContext ctxWith(final MapperGraph graph, final ExecutableElement method) {
        def ctx = new MapperContext(null)
        ctx.shape = new MapperShape(null, List.of(method))
        ctx.graph = graph
        ctx
    }

    private Node node(final Scope scope, final SourceLocation loc, final TypeMirror type) {
        new Node(Optional.of(type), loc, scope)
    }

    private Node node(final Scope scope, final TargetLocation loc, final TypeMirror type) {
        new Node(Optional.of(type), loc, scope)
    }

    private SourceLocation sourceLoc(final String... segments) {
        new SourceLocation(new AccessPath(segments as List))
    }

    private TargetLocation targetLoc(final String segment) {
        new TargetLocation(TargetPath.of(segment))
    }

    private TargetLocation returnRootLoc() {
        new TargetLocation(TargetPath.of(''))
    }

    private ExecutableElement mockMethod(
            final String name, final List<VariableElement> params, final TypeMirror returnType) {
        def m = Mock(ExecutableElement)
        m.simpleName >> nameOf(name)
        m.parameters >> params
        m.returnType >> returnType
        m.thrownTypes >> []
        m
    }

    private VariableElement mockParam(final String name) {
        def p = Mock(VariableElement)
        p.simpleName >> nameOf(name)
        def typeMock = Mock(TypeMirror)
        typeMock.toString() >> 'java.lang.Object'
        p.asType() >> typeMock
        p
    }

    private Name nameOf(final String value) {
        def n = Mock(Name)
        n.toString() >> value
        n
    }
}
