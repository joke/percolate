package io.github.joke.percolate.processor.internal.stages.generate

import io.github.joke.percolate.lib.javapoet.ClassName
import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.ProcessorOptions
import io.github.joke.percolate.processor.internal.graph.AccessPath
import io.github.joke.percolate.processor.internal.graph.AddOperation
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.ChildScope
import io.github.joke.percolate.processor.internal.graph.ElementLocation
import io.github.joke.percolate.processor.internal.graph.ExtractedPlan
import io.github.joke.percolate.processor.internal.graph.InputDecl
import io.github.joke.percolate.processor.internal.graph.Location
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.Operation
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.internal.graph.TargetPath
import io.github.joke.percolate.processor.internal.graph.Value
import io.github.joke.percolate.processor.model.MapperShape
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.ScopeCodegen
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * {@link BuildMethodBodies} seam, unit-tested directly: {@code build}/{@code renderMethod} are pure wiring,
 * exercised over mocked collaborators (design {@code decompose-engine-stages}, Phase 3 — the codegen exemplar).
 * Method bodies are emitted untagged here; whole-method docTag bracketing now lives on the {@code MethodSpec}
 * builder in {@link AssembleMapperType} (change {@code doc-tag-whole-methods}). {@link BuildMethodBodies.Walk}'s
 * own assembly logic is covered by {@link WalkSpec}.
 */
@Tag('unit')
class BuildMethodBodiesSpec extends Specification {

    def method = Mock(ExecutableElement)

    def 'build returns no bodies when the shape is absent'() {
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.graph = Mock(MapperGraph)

        expect:
        engine().build(ctx).bodies.empty
    }

    def 'build returns no bodies when the graph is absent'() {
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.shape = new MapperShape(Mock(TypeElement), [method])

        expect:
        engine().build(ctx).bodies.empty
    }

    def 'build renders one body per abstract method when both shape and graph are present'() {
        method.simpleName >> Stub(Name) { toString() >> 'map' }
        method.parameters >> []
        def graph = new MapperGraph()
        def scope = new MethodScope(method)
        def root = graph.apply(new AddValue(scope, new TargetLocation(TargetPath.of('')), Mock(TypeMirror), Nullability.NON_NULL))
        graph.markReturnRoot(root)
        graph.apply(new AddOperation('supply', { inputs -> CodeBlock.of('x') } as OperationCodegen, 1, false, [],
                new AddValue(scope, new TargetLocation(TargetPath.of('')), Mock(TypeMirror), Nullability.NON_NULL),
                Optional.empty(), [] as Set, []))
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.shape = new MapperShape(Mock(TypeElement), [method])
        ctx.graph = graph

        when:
        def result = engine().build(ctx)

        then:
        result.bodies.size() == 1
        result.bodies[0].method.is(method)
        result.bodies[0].body.toString() == 'return x;\n'
        result.members.empty
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private BuildMethodBodies engine() {
        new BuildMethodBodies(ProcessorOptions.builder()
                .debugGraphs(false)
                .customNullableAnnotations([] as Set)
                .localsFinal(false)
                .localsVar(false)
                .parametersFinal(false)
                .methodsFinal(false)
                .classesFinal(false)
                .docTags(false)
                .timeZone(Optional.empty())
                .build())
    }
}

/**
 * {@link BuildMethodBodies.Walk} unit-tested by mocking {@link MapperGraph}/{@link ExtractedPlan}/{@link HoistPlan}/
 * {@link TypeNameRenderer} — the pure assembly logic behind one method body, isolated from the irreducible
 * compiler-backed {@code TypeName.get(mirror)} leaf (design D7 of change {@code decompose-engine-stages}). Methods
 * that recurse into siblings ({@code renderInline}) are isolated with a {@code Spy}, per the {@code Grounding}
 * precedent (design D5): the recursion is genuinely self-referential over the plan's structure, not a separable
 * collaborator.
 */
@Tag('unit')
class WalkSpec extends Specification {

    MapperGraph graph = Mock()
    ExtractedPlan plan = Mock()
    HoistPlan hoist = Mock()
    MemberPlan memberPlan = Mock()
    LocalStyle style = new LocalStyle(false, false)
    TypeNameRenderer typeNameRenderer = Mock()

    // ---- renderLeaf: a bare leaf renders its bound lambda var or its source segment name -------------------------

    def 'renderLeaf renders a source-path leaf by its first segment name'() {
        def walk = walk()
        Value value = Mock()

        when:
        def result = walk.renderLeaf(value)

        then: 'getLoc is read twice — the instanceof check, then the SourceLocation cast'
        2 * value.loc >> new SourceLocation(AccessPath.of('in'))
        0 * _

        expect:
        result.toString() == 'in'
    }

    def 'renderLeaf fails fast for an unproducible leaf with neither a bound lambda var nor a source segment'() {
        def walk = walk()
        Value value = Mock()
        Location loc = Mock()

        when:
        walk.renderLeaf(value)

        then:
        1 * value.loc >> loc
        1 * value.id() >> 'v1'
        0 * _
        def error = thrown(IllegalStateException)

        expect:
        error.message.contains('v1')
    }

    def 'renderLeaf fails fast for a SourceLocation with no segments'() {
        def walk = walk()
        Value value = Mock()

        when:
        walk.renderLeaf(value)

        then: 'a SourceLocation with an empty path falls through to the same failure as an unrecognised Location'
        2 * value.loc >> new SourceLocation(new AccessPath([]))
        1 * value.id() >> 'v1'
        0 * _
        def error = thrown(IllegalStateException)

        expect:
        error.message.contains('v1')
    }

    // ---- localType: the sole compiler-backed leaf, delegated verbatim to TypeNameRenderer -------------------------

    def 'localType renders a typed Value\'s type through the injected TypeNameRenderer'() {
        def walk = walk()
        Value value = Mock()
        TypeMirror type = Mock()
        def rendered = ClassName.get('java.lang', 'String')

        when:
        def result = walk.localType(value)

        then:
        1 * value.type >> Optional.of(type)
        1 * typeNameRenderer.render(type) >> rendered
        0 * _

        expect:
        result.is(rendered)
    }

    def 'localType fails fast for an untyped hoisted Value'() {
        def walk = walk()
        Value value = Mock()

        when:
        walk.localType(value)

        then:
        1 * value.type >> Optional.empty()
        1 * value.id() >> 'v1'
        0 * _
        def error = thrown(IllegalStateException)

        expect:
        error.message.contains('v1')
    }

    // ---- typeToken: var when configured, otherwise the rendered type ---------------------------------------------

    def 'typeToken renders var when the useVar style is set, touching no TypeNameRenderer'() {
        def walk = walk(new LocalStyle(false, true))
        Value value = Mock()

        expect:
        walk.typeToken(value).toString() == 'var'
    }

    def 'typeToken renders the local type when useVar is not set'() {
        def walk = walk()
        Value value = Mock()
        TypeMirror type = Mock()

        when:
        def result = walk.typeToken(value)

        then:
        1 * value.type >> Optional.of(type)
        1 * typeNameRenderer.render(type) >> ClassName.get('java.lang', 'String')
        0 * _

        expect:
        result.toString() == 'java.lang.String'
    }

    // ---- emitLocal: one hoisted declaration statement, isolated via Spy from renderInline/typeToken ----------------

    def 'emitLocal emits a final local when the style requires final'() {
        def walk = Spy(BuildMethodBodies.Walk, constructorArgs: [graph, plan, hoist, memberPlan, new LocalStyle(true, false), typeNameRenderer])
        Value value = Mock()
        def builder = CodeBlock.builder()

        when:
        walk.emitLocal(builder, value)

        then:
        1 * hoist.declare(value) >> 'm'
        1 * walk.renderInline(value) >> CodeBlock.of('in')
        1 * walk.typeToken(value) >> CodeBlock.of('String')
        1 * walk._
        0 * _

        expect:
        builder.build().toString() == 'final String m = in;\n'
    }

    def 'emitLocal emits a non-final local when the style does not require final'() {
        def walk = Spy(BuildMethodBodies.Walk, constructorArgs: [graph, plan, hoist, memberPlan, new LocalStyle(false, false), typeNameRenderer])
        Value value = Mock()
        def builder = CodeBlock.builder()

        when:
        walk.emitLocal(builder, value)

        then:
        1 * hoist.declare(value) >> 'm'
        1 * walk.renderInline(value) >> CodeBlock.of('in')
        1 * walk.typeToken(value) >> CodeBlock.of('String')
        1 * walk._
        0 * _

        expect:
        builder.build().toString() == 'String m = in;\n'
    }

    // ---- renderPlain: an operand per port, positional and by name, isolated via Spy --------------------------------

    def 'renderPlain renders an operand for each port, positional and by name, in port order'() {
        def walk = spyWalk()
        Operation operation = Mock()
        def port0 = new Port('a', Mock(TypeMirror), Nullability.NON_NULL)
        def port1 = new Port('b', Mock(TypeMirror), Nullability.NON_NULL)
        Value source0 = Mock()
        Value source1 = Mock()
        OperationCodegen codegen = Mock()
        def rendered = CodeBlock.of('f(x, y)')

        when:
        def result = walk.renderPlain(operation)

        then:
        1 * operation.ports >> [port0, port1]
        1 * graph.portSource(operation, 'a') >> Optional.of(source0)
        1 * graph.portSource(operation, 'b') >> Optional.of(source1)
        1 * walk.renderOperand(source0) >> CodeBlock.of('x')
        1 * walk.renderOperand(source1) >> CodeBlock.of('y')
        1 * operation.memberRequests >> []
        1 * operation.codegen >> codegen
        1 * codegen.render { it.byGroupPosition(0).toString() == 'x' && it.byGroupPosition(1).toString() == 'y' } >> rendered
        1 * walk._
        0 * _

        expect:
        result.is(rendered)
    }

    def 'renderPlain resolves a member reference for a strategy-requested member, by dedup key'() {
        def walk = spyWalk()
        Operation operation = Mock()
        OperationCodegen codegen = Mock()
        def memberRequest = new io.github.joke.percolate.spi.MemberRequest(
                ClassName.get('java.time.format', 'DateTimeFormatter'), CodeBlock.of('null'), 'fmt-yyyy-MM-dd')
        def rendered = CodeBlock.of('FMT.format(x)')

        when:
        def result = walk.renderPlain(operation)

        then:
        1 * operation.ports >> []
        1 * operation.memberRequests >> [memberRequest]
        1 * memberPlan.reference('fmt-yyyy-MM-dd') >> CodeBlock.of('FMT')
        1 * operation.codegen >> codegen
        1 * codegen.render { it.member('fmt-yyyy-MM-dd').toString() == 'FMT' } >> rendered
        1 * walk._
        0 * _

        expect:
        result.is(rendered)
    }

    def 'renderPlain fails fast when a port has no source'() {
        def walk = spyWalk()
        Operation operation = Mock()
        def port = new Port('a', Mock(TypeMirror), Nullability.NON_NULL)

        when:
        walk.renderPlain(operation)

        then:
        1 * operation.ports >> [port]
        1 * graph.portSource(operation, 'a') >> Optional.empty()
        1 * walk._
        0 * _
        def error = thrown(IllegalStateException)

        expect:
        error.message.contains('a')
    }

    // ---- renderContainerMapping: not spied — a real integration check that the element root binds to its lambda --

    def 'renderContainerMapping binds the materialised element root to its lambda var, readable as a leaf in the child body'() {
        def walk = walk()
        Operation operation = Mock()
        def sourcePort = new Port('src', Mock(TypeMirror), Nullability.NON_NULL)
        Value sourceValue = Mock()
        ChildScope child = Mock()
        InputDecl elementInput = Mock()
        TypeMirror elementType = Mock()
        Value elementRoot = Mock()
        ScopeCodegen codegen = Mock()
        def rendered = CodeBlock.of('in.map(element -> element)')

        when:
        def result = walk.renderContainerMapping(operation)

        then:
        1 * operation.ports >> [sourcePort]
        1 * graph.portSource(operation, 'src') >> Optional.of(sourceValue)
        1 * hoist.isHoisted(sourceValue) >> true
        1 * hoist.reference(sourceValue) >> CodeBlock.of('in')
        1 * operation.childScope >> Optional.of(child)
        1 * child.elementInput >> elementInput
        1 * elementInput.type >> elementType
        1 * hoist.lambdaName(elementType) >> 'element'
        1 * graph.valuesIn(child) >> Stream.of(elementRoot)
        1 * elementRoot.loc >> new ElementLocation()
        1 * child.returnRoot >> elementRoot
        2 * plan.chosenProducer(elementRoot) >> Optional.empty()
        1 * operation.codegen >> codegen
        1 * codegen.weave(CodeBlock.of('in'), 'element', CodeBlock.of('$N', 'element')) >> rendered
        0 * _

        expect:
        result.is(rendered)
    }

    // ---- renderOperand: a variable reference when hoisted, otherwise the inline expression, isolated via Spy ------

    def 'renderOperand references a hoisted Value by name, without rendering it inline'() {
        def walk = spyWalk()
        Value value = Mock()
        def ref = CodeBlock.of('m')

        when:
        def result = walk.renderOperand(value)

        then:
        1 * hoist.isHoisted(value) >> true
        1 * hoist.reference(value) >> ref
        1 * walk._
        0 * _

        expect:
        result.is(ref)
    }

    def 'renderOperand renders an un-hoisted Value inline'() {
        def walk = spyWalk()
        Value value = Mock()
        def inline = CodeBlock.of('x')

        when:
        def result = walk.renderOperand(value)

        then:
        1 * hoist.isHoisted(value) >> false
        1 * walk.renderInline(value) >> inline
        1 * walk._
        0 * _

        expect:
        result.is(inline)
    }

    // ---- renderInline: leaf, plain producer, or container-mapping dispatch, isolated via Spy -----------------------

    def 'renderInline renders an unproduced Value as a leaf'() {
        def walk = spyWalk()
        Value value = Mock()
        def leaf = CodeBlock.of('in')

        when:
        def result = walk.renderInline(value)

        then:
        1 * plan.chosenProducer(value) >> Optional.empty()
        1 * walk.renderLeaf(value) >> leaf
        1 * walk._
        0 * _

        expect:
        result.is(leaf)
    }

    def 'renderInline dispatches a plain producer to renderPlain'() {
        def walk = spyWalk()
        Value value = Mock()
        Operation operation = Mock()
        def rendered = CodeBlock.of('x')

        when:
        def result = walk.renderInline(value)

        then:
        1 * plan.chosenProducer(value) >> Optional.of(operation)
        1 * operation.childScope >> Optional.empty()
        1 * walk.renderPlain(operation) >> rendered
        1 * walk._
        0 * _

        expect:
        result.is(rendered)
    }

    def 'renderInline dispatches a scope-owning producer to renderContainerMapping'() {
        def walk = spyWalk()
        Value value = Mock()
        Operation operation = Mock()
        ChildScope childScope = Mock()
        def rendered = CodeBlock.of('x.map(y -> y)')

        when:
        def result = walk.renderInline(value)

        then:
        1 * plan.chosenProducer(value) >> Optional.of(operation)
        1 * operation.childScope >> Optional.of(childScope)
        1 * walk.renderContainerMapping(operation) >> rendered
        1 * walk._
        0 * _

        expect:
        result.is(rendered)
    }

    // ---- renderMethodBody / renderScopeBody: hoist the locals, then return the inline root, via Spy -----------------

    def 'renderMethodBody emits no locals and returns the inline root expression when nothing hoists'() {
        def walk = spyWalk()
        Value root = Mock()

        when:
        def result = walk.renderMethodBody(root)

        then:
        1 * walk.hoistedInScope(root) >> []
        1 * walk.renderInline(root) >> CodeBlock.of('x')
        1 * walk._
        0 * _

        expect:
        result.toString() == 'return x;\n'
    }

    def 'renderMethodBody emits one local statement per hoisted Value before the return'() {
        def walk = spyWalk()
        Value root = Mock()
        Value hoisted = Mock()

        when:
        def result = walk.renderMethodBody(root)

        then:
        1 * walk.hoistedInScope(root) >> [hoisted]
        1 * walk.emitLocal({ it != null }, hoisted) >> { CodeBlock.Builder builder, Value v -> builder.addStatement('var m = in') }
        1 * walk.renderInline(root) >> CodeBlock.of('m')
        1 * walk._
        0 * _

        expect:
        result.toString() == 'var m = in;\nreturn m;\n'
    }

    def 'renderScopeBody stays an inline expression when the child scope hoists nothing'() {
        def walk = spyWalk()
        Value root = Mock()
        def inline = CodeBlock.of('x')

        when:
        def result = walk.renderScopeBody(root)

        then:
        1 * walk.hoistedInScope(root) >> []
        1 * walk.renderInline(root) >> inline
        1 * walk._
        0 * _

        expect:
        result.is(inline)
    }

    def 'renderScopeBody renders a block with locals then a return when the child scope hoists something'() {
        def walk = spyWalk()
        Value root = Mock()
        Value hoisted = Mock()

        when:
        def result = walk.renderScopeBody(root)

        then:
        1 * walk.hoistedInScope(root) >> [hoisted]
        1 * walk.emitLocal({ it != null }, hoisted) >> { CodeBlock.Builder builder, Value v -> builder.addStatement('var m = in') }
        1 * walk.renderInline(root) >> CodeBlock.of('m')
        1 * walk._
        0 * _

        expect:
        result.toString() == '{\n  var m = in;\n  return m;\n}'
    }

    // ---- collectHoisted / hoistedInScope: post-order traversal over the plan's port sources -----------------------

    def 'hoistedInScope collects a hoisted producer\'s ports before itself, excluding the root'() {
        def walk = walk()
        Value root = Mock()
        Value child = Mock()
        Value leaf = Mock()
        Operation rootProducer = Mock()
        Operation childProducer = Mock()

        when:
        def result = walk.hoistedInScope(root)

        then:
        1 * plan.chosenProducer(root) >> Optional.of(rootProducer)
        1 * graph.portSourcesOf(rootProducer) >> Stream.of(child)
        1 * plan.chosenProducer(child) >> Optional.of(childProducer)
        1 * graph.portSourcesOf(childProducer) >> Stream.of(leaf)
        1 * plan.chosenProducer(leaf) >> Optional.empty()
        1 * hoist.isHoisted(child) >> true
        0 * _

        expect: 'the root is excluded even when hoisted (isHoisted is never even asked for it); the leaf has no producer'
        result == [child]
    }

    def 'hoistedInScope visits a Value shared by two consumers only once'() {
        def walk = walk()
        Value root = Mock()
        Value shared = Mock()
        Operation rootProducer = Mock()

        when:
        def result = walk.hoistedInScope(root)

        then:
        1 * plan.chosenProducer(root) >> Optional.of(rootProducer)
        1 * graph.portSourcesOf(rootProducer) >> Stream.of(shared, shared)
        1 * plan.chosenProducer(shared) >> Optional.empty()
        0 * _

        expect: 'shared has no chosen producer, so it is a base case and is never added, regardless of hoist status'
        result == []
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private BuildMethodBodies.Walk walk(final LocalStyle localStyle = style) {
        new BuildMethodBodies.Walk(graph, plan, hoist, memberPlan, localStyle, typeNameRenderer)
    }

    private BuildMethodBodies.Walk spyWalk() {
        Spy(BuildMethodBodies.Walk, constructorArgs: [graph, plan, hoist, memberPlan, style, typeNameRenderer])
    }
}
