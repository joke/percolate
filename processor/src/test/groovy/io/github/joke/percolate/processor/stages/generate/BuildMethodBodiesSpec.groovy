package io.github.joke.percolate.processor.stages.generate

import io.github.joke.percolate.processor.test.TestGroups

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.model.MapperShape
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.Nullability
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
        graph.addEdge(param, returnRoot, Edge.realised(Weights.NOOP, DIRECT_ASSIGN, 'DirectAssign'))
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
        graph.addEdge(param, field, Edge.realised(Weights.STEP_GETTER, GETTER_FIRST_NAME, 'GetterPathResolver'))
        graph.addEdge(field, returnRoot, Edge.realised(Weights.NOOP, DIRECT_ASSIGN, 'DirectAssign'))
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

        graph.addEdge(param, firstNameSrc, Edge.realised(Weights.STEP_GETTER, GETTER_FIRST_NAME, 'GetterPathResolver'))
        graph.addEdge(param, lastNameSrc, Edge.realised(Weights.STEP_GETTER, GETTER_LAST_NAME, 'GetterPathResolver'))
        graph.addEdge(firstNameSrc, firstNameSlot, Edge.realised(Weights.NOOP, DIRECT_ASSIGN, 'DirectAssign'))
        graph.addEdge(lastNameSrc, lastNameSlot, Edge.realised(Weights.NOOP, DIRECT_ASSIGN, 'DirectAssign'))

        // The constructor codegen rides on each operand edge (the fan-in shares one producer codegen instance).
        EdgeCodegen ctorCodegen = { vars, inputs ->
            CodeBlock.of('new Human($L, $L)', inputs.byName('firstName'), inputs.byName('lastName'))
        } as EdgeCodegen
        def firstEdge = Edge.realised(Weights.STEP, ctorCodegen, 'io.github.joke.percolate.spi.builtins.ConstructorCall')
        def lastEdge = Edge.realised(Weights.STEP, ctorCodegen, 'io.github.joke.percolate.spi.builtins.ConstructorCall')
        graph.addEdge(firstNameSlot, returnRoot, firstEdge)
        graph.addEdge(lastNameSlot, returnRoot, lastEdge)
        // The constructor's group marks returnRoot as an AND fan-in (protected from PlanView's OR-pruning).
        def ctorGroup = TestGroups.of(returnRoot, [firstNameSlot, lastNameSlot], 'io.github.joke.percolate.spi.builtins.ConstructorCall', [firstEdge, lastEdge] as Set, graph)
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

        graph.addEdge(param, ageSrc, Edge.realised(Weights.STEP_GETTER, GETTER_AGE, 'GetterPathResolver'))
        // the folded conversion: a plain realised edge, no enclosing single-slot group
        graph.addEdge(ageSrc, ageSlot, Edge.realised(Weights.STEP, BOX_LONG, 'io.github.joke.percolate.spi.builtins.BoxingBridge'))

        EdgeCodegen ctorCodegen = { vars, inputs ->
            CodeBlock.of('new Person($L)', inputs.byName('age'))
        } as EdgeCodegen
        graph.addEdge(ageSlot, returnRoot, Edge.realised(Weights.STEP, ctorCodegen, 'io.github.joke.percolate.spi.builtins.ConstructorCall'))

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

        graph.addEdge(param, elemSlot, Edge.realised(Weights.NOOP, DIRECT_ASSIGN, 'DirectAssign'))
        EdgeCodegen listCodegen = { vars, inputs -> CodeBlock.of('$T.of($L)', List, inputs.single()) } as EdgeCodegen
        graph.addEdge(elemSlot, returnRoot, Edge.realised(Weights.CONTAINER, listCodegen, 'io.github.joke.percolate.spi.builtins.ListCollect'))

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

        graph.addEdge(param, innerElem, Edge.realised(Weights.NOOP, DIRECT_ASSIGN, 'DirectAssign'))
        EdgeCodegen setCodegen = { vars, inputs -> CodeBlock.of('$T.of($L)', Set, inputs.single()) } as EdgeCodegen
        EdgeCodegen optCodegen = { vars, inputs -> CodeBlock.of('$T.of($L)', Optional, inputs.single()) } as EdgeCodegen
        graph.addEdge(innerElem, outerElem, Edge.realised(Weights.CONTAINER, setCodegen, 'io.github.joke.percolate.spi.builtins.SetCollect'))
        graph.addEdge(outerElem, returnRoot, Edge.realised(Weights.CONTAINER, optCodegen, 'io.github.joke.percolate.spi.builtins.OptionalCollect'))

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

        EdgeCodegen aliveCodegen = { vars, inputs -> CodeBlock.of('$T.of($L)', List, inputs.single()) } as EdgeCodegen
        EdgeCodegen deadCodegen = { vars, inputs -> CodeBlock.of('DEAD($L)', inputs.single()) } as EdgeCodegen
        graph.addEdge(param, aliveSlot, Edge.realised(Weights.NOOP, DIRECT_ASSIGN, 'DirectAssign'))
        def aliveEdge = Edge.realised(Weights.CONTAINER, aliveCodegen, 'io.github.joke.percolate.spi.builtins.ListCollect')
        def deadEdge = Edge.realised(Weights.CONTAINER, deadCodegen, 'io.github.joke.percolate.spi.builtins.OptionalUnwrap')
        graph.addEdge(aliveSlot, returnRoot, aliveEdge)
        graph.addEdge(deadSlot, returnRoot, deadEdge)

        // Two SAT/UNSAT sibling producers of returnRoot; the UNSAT one's edge must be pruned from the plan.
        def aliveGroup = TestGroups.of(returnRoot, [aliveSlot], 'io.github.joke.percolate.spi.builtins.ListCollect', [aliveEdge] as Set, graph)
        def deadGroup = TestGroups.of(returnRoot, [deadSlot], 'io.github.joke.percolate.spi.builtins.OptionalUnwrap', [deadEdge] as Set, graph)
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

    def 'a constant producer renders the literal operand with no incoming variable and no null guard'() {
        given:
        def method = mockMethod('map', [mockParam('in')], TypeUniverse.STRING)
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def constNode = new Node(Optional.empty(), new ConstantLocation('ACTIVE'), scope)
        constNode.setTyping(TypeUniverse.STRING, Nullability.NON_NULL)
        def statusSlot = node(scope, targetLoc('status'), TypeUniverse.STRING)
        def returnRoot = node(scope, returnRootLoc(), TypeUniverse.STRING)
        [constNode, statusSlot, returnRoot].each { graph.addNode(it) }

        EdgeCodegen literal = { vars, inputs -> CodeBlock.of('$S', 'ACTIVE') } as EdgeCodegen
        graph.addEdge(constNode, statusSlot, Edge.realised(Weights.STEP, literal, 'io.github.joke.percolate.spi.builtins.ConstantValue'))
        EdgeCodegen ctorCodegen = { vars, inputs -> CodeBlock.of('new Thing($L)', inputs.byName('status')) } as EdgeCodegen
        def ctorEdge = Edge.realised(Weights.STEP, ctorCodegen, 'io.github.joke.percolate.spi.builtins.ConstructorCall')
        graph.addEdge(statusSlot, returnRoot, ctorEdge)
        def ctorGroup = TestGroups.of(returnRoot, [statusSlot], 'io.github.joke.percolate.spi.builtins.ConstructorCall', [ctorEdge] as Set, graph)
        graph.recordGroupOutcome(GroupOutcome.sat(ctorGroup))

        def ctx = ctxWith(graph, method)

        when:
        def bodies = new BuildMethodBodies().build(ctx)

        then:
        bodies.size() == 1
        bodies[0].body.toString().trim() == 'return new Thing("ACTIVE");'
        !bodies[0].body.toString().contains('requireNonNull')
    }

    def 'a default-coalesced operand feeding a non-null slot renders the coalesce with no requireNonNull guard'() {
        given: 'a nullable source folded through a default coalesce into a NON_NULL target slot'
        def method = mockMethod('map', [mockParam('in')], TypeUniverse.STRING)
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def param = node(scope, sourceLoc('in'), TypeUniverse.STRING)
        def nameSrc = new Node(Optional.empty(), sourceLoc('in', 'name'), scope)
        nameSrc.setTyping(TypeUniverse.STRING, Nullability.NULLABLE)
        // The coalesced producer IS the target leaf, typed NON_NULL by the assembly (see design D6 / nullability spec).
        def nameSlot = new Node(Optional.empty(), targetLoc('name'), scope)
        nameSlot.setTyping(TypeUniverse.STRING, Nullability.NON_NULL)
        def returnRoot = node(scope, returnRootLoc(), TypeUniverse.STRING)
        [param, nameSrc, nameSlot, returnRoot].each { graph.addNode(it) }

        EdgeCodegen getName = { vars, inputs -> CodeBlock.of('$L.getName()', inputs.single()) } as EdgeCodegen
        graph.addEdge(param, nameSrc, Edge.realised(Weights.STEP_GETTER, getName, 'GetterPathResolver'))
        // The default coalesce: a folded CONVERSION edge that wraps the source value, carrying no consumer slot
        // (so the nullable source flows in unguarded — the coalesce itself handles the null).
        EdgeCodegen coalesce = { vars, inputs ->
            CodeBlock.of('$T.requireNonNullElse($L, $S)', Objects, inputs.single(), 'unknown')
        } as EdgeCodegen
        graph.addEdge(nameSrc, nameSlot, Edge.realised(Weights.NOOP, coalesce, 'io.github.joke.percolate.spi.builtins.DefaultValue'))

        EdgeCodegen ctorCodegen = { vars, inputs -> CodeBlock.of('new Thing($L)', inputs.byName('name')) } as EdgeCodegen
        def ctorEdge = Edge.realised(Weights.STEP, ctorCodegen, 'io.github.joke.percolate.spi.builtins.ConstructorCall')
        graph.addEdge(nameSlot, returnRoot, ctorEdge)
        def ctorGroup = TestGroups.of(returnRoot, [nameSlot], 'io.github.joke.percolate.spi.builtins.ConstructorCall', [ctorEdge] as Set, graph)
        graph.recordGroupOutcome(GroupOutcome.sat(ctorGroup))

        def ctx = ctxWith(graph, method)

        when:
        def bodies = new BuildMethodBodies().build(ctx)

        then: 'the operand is the coalesce expression, with no plain null guard wrapping it'
        bodies.size() == 1
        bodies[0].body.toString().trim() == 'return new Thing(java.util.Objects.requireNonNullElse(in.getName(), "unknown"));'

        and: 'the coalesced producer is read as NON_NULL, so the non-null guard is suppressed'
        nameSlot.nullability.get() == Nullability.NON_NULL
        !bodies[0].body.toString().contains('source for slot')
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
