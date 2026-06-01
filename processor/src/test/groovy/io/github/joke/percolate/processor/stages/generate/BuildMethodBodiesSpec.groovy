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
    static final EdgeCodegen GETTER_AGE = { vars, inputs ->
        CodeBlock.of('$L.getAge()', inputs.single())
    } as EdgeCodegen
    static final EdgeCodegen BOX_LONG = { vars, inputs ->
        CodeBlock.of('$T.valueOf($L)', Long, inputs.single())
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
        def ctorGroup = ExpansionGroup.of(
                returnRoot,
                [firstNameSlot, lastNameSlot],
                ctorCodegen,
                'io.github.joke.percolate.spi.builtins.ConstructorCall',
                [firstEdge, lastEdge] as Set,
                graph)
        graph.addGroup(ctorGroup)
        graph.recordGroupOutcome(GroupOutcome.sat(ctorGroup))

        def ctx = ctxWith(graph, method)

        when:
        def bodies = new BuildMethodBodies().build(ctx)

        then:
        bodies.size() == 1
        bodies[0].body.toString().trim() == 'return new Human(person.getFirstName(), person.getLastName());'
    }

    def 'folded conversion edge renders mid-chain without its own group (unify-expansion-spi de-risk)'() {
        // Post-fold shape: a CONVERSION step (box) is NOT its own ExpansionGroup; it is a realised edge
        // inside the constructor slot's producer chain. Proves BuildMethodBodies renders the conversion from
        // its edge via renderScalarEdge regardless of group-root status — the gating risk for the fold design.
        given:
        def method = mockMethod('map', [mockParam('person')], TypeUniverse.STRING)
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def param = node(scope, sourceLoc('person'), TypeUniverse.STRING)
        def ageSrc = node(scope, sourceLoc('person', 'age'), TypeUniverse.INT)
        def ageSlot = node(scope, targetLoc('age'), TypeUniverse.LONG)
        def returnRoot = node(scope, returnRootLoc(), TypeUniverse.STRING)

        [param, ageSrc, ageSlot, returnRoot].each { graph.addNode(it) }

        graph.addEdge(Edge.realised(param, ageSrc, Weights.STEP_GETTER, GETTER_AGE, 'GetterPathResolver'))
        // the folded conversion: a plain realised edge, no enclosing single-slot group
        graph.addEdge(Edge.realised(ageSrc, ageSlot, Weights.STEP, BOX_LONG, 'io.github.joke.percolate.spi.builtins.BoxingBridge'))

        GroupCodegen ctorCodegen = { vars, inputs ->
            CodeBlock.of('new Person($L)', inputs.byName('age'))
        } as GroupCodegen
        def passThrough = { vars, inputs -> CodeBlock.of('$L', inputs.single()) } as EdgeCodegen
        def ctorEdge = Edge.realised(ageSlot, returnRoot, Weights.STEP, passThrough, 'io.github.joke.percolate.spi.builtins.ConstructorCall')
        graph.addEdge(ctorEdge)
        def ctorGroup = ExpansionGroup.of(
                returnRoot,
                [ageSlot],
                ctorCodegen,
                'io.github.joke.percolate.spi.builtins.ConstructorCall',
                [ctorEdge] as Set,
                graph)
        graph.addGroup(ctorGroup)
        graph.recordGroupOutcome(GroupOutcome.sat(ctorGroup))

        def ctx = ctxWith(graph, method)

        when:
        def bodies = new BuildMethodBodies().build(ctx)

        then:
        bodies.size() == 1
        bodies[0].body.toString().trim() == 'return new Person(java.lang.Long.valueOf(person.getAge()));'
    }

    def 'container group slot is named by its element role'() {
        given:
        def method = mockMethod('map', [mockParam('value')], TypeUniverse.STRING)
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def param = node(scope, sourceLoc('value'), TypeUniverse.STRING)
        def elemSlot = node(scope, new ElementLocation(), TypeUniverse.STRING)
        def returnRoot = node(scope, returnRootLoc(), TypeUniverse.STRING)

        [param, elemSlot, returnRoot].each { graph.addNode(it) }

        graph.addEdge(Edge.realised(param, elemSlot, Weights.NOOP, DIRECT_ASSIGN, 'DirectAssign'))
        def collectEdge = Edge.realised(elemSlot, returnRoot, Weights.CONTAINER, DIRECT_ASSIGN, 'io.github.joke.percolate.spi.builtins.ListCollect')
        graph.addEdge(collectEdge)

        GroupCodegen listCodegen = { vars, inputs -> CodeBlock.of('$T.of($L)', List, inputs.single()) } as GroupCodegen
        def listGroup = ExpansionGroup.of(
                returnRoot,
                [elemSlot],
                listCodegen,
                'io.github.joke.percolate.spi.builtins.ListCollect',
                [collectEdge] as Set,
                graph)
        graph.addGroup(listGroup)
        graph.recordGroupOutcome(GroupOutcome.sat(listGroup))

        def ctx = ctxWith(graph, method)

        when:
        def bodies = new BuildMethodBodies().build(ctx)

        then:
        noExceptionThrown()
        bodies.size() == 1
        bodies[0].body.toString().trim() == 'return java.util.List.of(value);'
    }

    def 'nested container groups compose without slot-name failure'() {
        given:
        def method = mockMethod('map', [mockParam('value')], TypeUniverse.STRING)
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def param = node(scope, sourceLoc('value'), TypeUniverse.STRING)
        def innerElem = node(scope, new ElementLocation(), TypeUniverse.STRING)
        def outerElem = node(scope, new ElementLocation(), TypeUniverse.STRING)
        def returnRoot = node(scope, returnRootLoc(), TypeUniverse.STRING)

        [param, innerElem, outerElem, returnRoot].each { graph.addNode(it) }

        graph.addEdge(Edge.realised(param, innerElem, Weights.NOOP, DIRECT_ASSIGN, 'DirectAssign'))
        def setEdge = Edge.realised(innerElem, outerElem, Weights.CONTAINER, DIRECT_ASSIGN, 'io.github.joke.percolate.spi.builtins.SetCollect')
        def optEdge = Edge.realised(outerElem, returnRoot, Weights.CONTAINER, DIRECT_ASSIGN, 'io.github.joke.percolate.spi.builtins.OptionalCollect')
        graph.addEdge(setEdge)
        graph.addEdge(optEdge)

        GroupCodegen setCodegen = { vars, inputs -> CodeBlock.of('$T.of($L)', Set, inputs.single()) } as GroupCodegen
        GroupCodegen optCodegen = { vars, inputs -> CodeBlock.of('$T.of($L)', Optional, inputs.single()) } as GroupCodegen
        def setGroup = ExpansionGroup.of(
                outerElem,
                [innerElem],
                setCodegen,
                'io.github.joke.percolate.spi.builtins.SetCollect',
                [setEdge] as Set,
                graph)
        def optGroup = ExpansionGroup.of(
                returnRoot,
                [outerElem],
                optCodegen,
                'io.github.joke.percolate.spi.builtins.OptionalCollect',
                [optEdge] as Set,
                graph)
        graph.addGroup(setGroup)
        graph.addGroup(optGroup)
        graph.recordGroupOutcome(GroupOutcome.sat(setGroup))
        graph.recordGroupOutcome(GroupOutcome.sat(optGroup))

        def ctx = ctxWith(graph, method)

        when:
        def bodies = new BuildMethodBodies().build(ctx)

        then:
        noExceptionThrown()
        bodies.size() == 1
        bodies[0].body.toString().trim() == 'return java.util.Optional.of(java.util.Set.of(value));'
    }

    def 'dead UNSAT sibling group is not rendered'() {
        given:
        def method = mockMethod('map', [mockParam('value')], TypeUniverse.STRING)
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def param = node(scope, sourceLoc('value'), TypeUniverse.STRING)
        def aliveSlot = node(scope, new ElementLocation(), TypeUniverse.STRING)
        def deadSlot = node(scope, new ElementLocation('dead'), TypeUniverse.STRING)
        def returnRoot = node(scope, returnRootLoc(), TypeUniverse.STRING)

        [param, aliveSlot, deadSlot, returnRoot].each { graph.addNode(it) }

        graph.addEdge(Edge.realised(param, aliveSlot, Weights.NOOP, DIRECT_ASSIGN, 'DirectAssign'))
        def aliveEdge = Edge.realised(aliveSlot, returnRoot, Weights.CONTAINER, DIRECT_ASSIGN, 'io.github.joke.percolate.spi.builtins.ListCollect')
        def deadEdge = Edge.realised(deadSlot, returnRoot, Weights.CONTAINER, DIRECT_ASSIGN, 'io.github.joke.percolate.spi.builtins.OptionalUnwrap')
        graph.addEdge(aliveEdge)
        graph.addEdge(deadEdge)

        GroupCodegen aliveCodegen = { vars, inputs -> CodeBlock.of('$T.of($L)', List, inputs.single()) } as GroupCodegen
        GroupCodegen deadCodegen = { vars, inputs -> CodeBlock.of('DEAD($L)', inputs.single()) } as GroupCodegen
        def aliveGroup = ExpansionGroup.of(
                returnRoot, [aliveSlot], aliveCodegen,
                'io.github.joke.percolate.spi.builtins.ListCollect', [aliveEdge] as Set, graph)
        def deadGroup = ExpansionGroup.of(
                returnRoot, [deadSlot], deadCodegen,
                'io.github.joke.percolate.spi.builtins.OptionalUnwrap', [deadEdge] as Set, graph)
        graph.addGroup(aliveGroup)
        graph.addGroup(deadGroup)
        graph.recordGroupOutcome(GroupOutcome.sat(aliveGroup))
        graph.recordGroupOutcome(GroupOutcome.unsatNoPlan(deadGroup, deadSlot))

        def ctx = ctxWith(graph, method)

        when:
        def bodies = new BuildMethodBodies().build(ctx)

        then:
        noExceptionThrown()
        bodies.size() == 1
        bodies[0].body.toString().trim() == 'return java.util.List.of(value);'
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

    private Node node(final Scope scope, final ElementLocation loc, final TypeMirror type) {
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
