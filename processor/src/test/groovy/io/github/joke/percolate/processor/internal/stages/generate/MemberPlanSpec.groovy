package io.github.joke.percolate.processor.internal.stages.generate

import io.github.joke.percolate.lib.javapoet.ClassName
import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.lib.javapoet.TypeName
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.HelperStyle
import io.github.joke.percolate.processor.MemberVisibility
import io.github.joke.percolate.processor.internal.graph.AddOperation
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.ExtractedPlan
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.internal.graph.TargetPath
import io.github.joke.percolate.processor.internal.graph.Value
import io.github.joke.percolate.spi.MemberRequest
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link MemberPlan} seam, unit-tested directly over a real {@link MapperGraph}/{@link ExtractedPlan} (the
 * {@link HoistPlan} precedent): collects every {@link MemberRequest} reachable from any method's winning plan across
 * the whole mapper, deduplicates by {@code dedupKey}, and names each distinct member — the class-scoped sibling of
 * {@link HoistPlan}'s method-scoped local naming.
 */
@Tag('unit')
class MemberPlanSpec extends Specification {

    static final OperationCodegen OP = { inputs -> CodeBlock.of('x') } as OperationCodegen
    static final ClassName FORMATTER = ClassName.get('java.time.format', 'DateTimeFormatter')

    MemberPlanFactory memberPlanFactory = new MemberPlanFactory(new HoistPlanFactory(), new HelperStyle(MemberVisibility.PRIVATE, true))

    @Shared TypeMirror STRING = Mock()

    def method = Mock(ExecutableElement) {
        getSimpleName() >> Stub(Name) { toString() >> 'map' }
        getParameters() >> []
    }
    MethodScope scope = new MethodScope(method)
    MapperGraph graph = new MapperGraph()
    MapperContext ctx = new MapperContext(Mock(TypeElement))

    def 'two operations sharing a dedup key resolve to exactly one field'() {
        def request = MemberRequest.field(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'yyyy-MM-dd'), 'fmt-yyyy-MM-dd')
        def a = target('a')
        def b = target('b')
        operation(a, [request])
        operation(b, [request])
        def root = target('')
        graph.markReturnRoot(root)
        assemble(root, [a, b])
        def plan = ExtractedPlan.extract(graph)

        expect:
        memberPlanFactory.forMapper(graph, plan, ctx).fields().size() == 1
    }

    def 'distinct dedup keys resolve to distinct field names'() {
        def requestA = MemberRequest.field(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'yyyy-MM-dd'), 'fmt-yyyy-MM-dd')
        def requestB = MemberRequest.field(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'dd.MM.yyyy'), 'fmt-dd.MM.yyyy')
        def a = target('a')
        def b = target('b')
        operation(a, [requestA])
        operation(b, [requestB])
        def root = target('')
        graph.markReturnRoot(root)
        assemble(root, [a, b])
        def plan = ExtractedPlan.extract(graph)
        def memberPlan = memberPlanFactory.forMapper(graph, plan, ctx)

        expect:
        memberPlan.reference('fmt-yyyy-MM-dd').toString() != memberPlan.reference('fmt-dd.MM.yyyy').toString()
    }

    def 'a mapper whose operations request no member declares no fields'() {
        def root = target('')
        graph.markReturnRoot(root)
        operation(root, [])
        def plan = ExtractedPlan.extract(graph)

        expect:
        memberPlanFactory.forMapper(graph, plan, ctx).fields().empty
    }

    def 'each distinct member is emitted once as a field, initialized with the requested initializer'() {
        def request = MemberRequest.field(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'yyyy-MM-dd'), 'fmt-yyyy-MM-dd')
        def a = target('a')
        operation(a, [request])
        def root = target('')
        graph.markReturnRoot(root)
        assemble(root, [a])
        def plan = ExtractedPlan.extract(graph)

        when:
        def fields = memberPlanFactory.forMapper(graph, plan, ctx).fields()

        then:
        fields.size() == 1
        fields[0].type == FORMATTER
        fields[0].initializer.toString().contains('DateTimeFormatter.ofPattern("yyyy-MM-dd")')
    }

    def 'a method request is emitted as a method with the requested return type, parameters and body'() {
        def request = MemberRequest.method(
                'assemblePerson', TypeName.INT, [new MemberRequest.Parameter(TypeName.get(String), 'name')],
                CodeBlock.of('return 1;\n'), 'setter:Person:name')
        def a = target('a')
        operation(a, [request])
        def root = target('')
        graph.markReturnRoot(root)
        assemble(root, [a])
        def plan = ExtractedPlan.extract(graph)

        when:
        def memberPlan = memberPlanFactory.forMapper(graph, plan, ctx)

        then:
        memberPlan.fields().empty

        expect:
        with(memberPlan.methods()) {
            size() == 1
            it[0].name() == 'assemblePerson'
            it[0].returnType() == TypeName.INT
            it[0].parameters()*.name() == ['name']
            it[0].code().toString() == 'return 1;\n'
        }
    }

    def 'both request kinds share one dedup namespace, so a field and a method never collide on a name'() {
        def field = MemberRequest.field(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'yyyy'), 'fmt')
        def method = MemberRequest.method(
                'dateTimeFormatter', TypeName.INT, [], CodeBlock.of('return 1;\n'), 'helper')
        def a = target('a')
        def b = target('b')
        operation(a, [field])
        operation(b, [method])
        def root = target('')
        graph.markReturnRoot(root)
        assemble(root, [a, b])
        def plan = ExtractedPlan.extract(graph)
        def memberPlan = memberPlanFactory.forMapper(graph, plan, ctx)

        expect:
        memberPlan.reference('fmt').toString() != memberPlan.reference('helper').toString()
    }

    def 'a field and a method request under one dedup key report a permanent conflict'() {
        def field = MemberRequest.field(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'yyyy'), 'clash')
        def method = MemberRequest.method('helper', TypeName.INT, [], CodeBlock.of('return 1;\n'), 'clash')
        def a = target('a')
        def b = target('b')
        operation(a, [field], 'opA')
        operation(b, [method], 'opB')
        def root = target('')
        graph.markReturnRoot(root)
        assemble(root, [a, b])
        def plan = ExtractedPlan.extract(graph)

        when:
        memberPlanFactory.forMapper(graph, plan, ctx)

        then:
        ctx.diagnostics.size() == 1

        expect:
        with(ctx.diagnostics[0]) {
            permanent
            message.contains('clash') && message.contains('opA') && message.contains('opB')
        }
    }

    def 'the default helper style reproduces the previous private static final field'() {
        expect:
        style(MemberVisibility.PRIVATE, true).fieldModifiers() as List ==
                [Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL]
    }

    def 'percolate.helpers.visibility sets the access modifier of both kinds'() {
        expect:
        style(visibility, true).memberModifiers() as List == access + [Modifier.STATIC]
        style(visibility, true).fieldModifiers() as List == access + [Modifier.STATIC, Modifier.FINAL]

        where:
        visibility                   | access
        MemberVisibility.PRIVATE     | [Modifier.PRIVATE]
        MemberVisibility.PACKAGE     | []
        MemberVisibility.PROTECTED   | [Modifier.PROTECTED]
        MemberVisibility.PUBLIC      | [Modifier.PUBLIC]
    }

    def 'percolate.helpers.static drops static from both kinds, and a field stays final regardless'() {
        expect:
        style(MemberVisibility.PRIVATE, false).memberModifiers() as List == [Modifier.PRIVATE]
        style(MemberVisibility.PRIVATE, false).fieldModifiers() as List == [Modifier.PRIVATE, Modifier.FINAL]
    }

    def 'the two helper options compose'() {
        expect:
        style(MemberVisibility.PUBLIC, false).memberModifiers() as List == [Modifier.PUBLIC]
    }

    def 'referencing an unregistered dedup key fails fast'() {
        def root = target('')
        graph.markReturnRoot(root)
        operation(root, [])
        def plan = ExtractedPlan.extract(graph)

        when:
        memberPlanFactory.forMapper(graph, plan, ctx).reference('unknown')

        then:
        def error = thrown(IllegalStateException)

        expect:
        error.message.contains('unknown')
    }

    def 'fieldBase names the field after a ClassName\'s lower-camel simple name'() {
        expect:
        memberPlanFactory.fieldBase(FORMATTER) == 'dateTimeFormatter'
    }

    def 'fieldBase falls back to "member" for a non-ClassName field type (e.g. a primitive)'() {
        expect:
        memberPlanFactory.fieldBase(TypeName.INT) == 'member'
    }

    def 'memberBase takes a field request\'s base from its type and a method request\'s from its own hint'() {
        expect:
        memberPlanFactory.memberBase(MemberRequest.field(FORMATTER, CodeBlock.of('x'), 'k')) == 'dateTimeFormatter'
        memberPlanFactory.memberBase(
                MemberRequest.method('assemblePerson', TypeName.INT, [], CodeBlock.of('x'), 'k')) == 'assemblePerson'
    }

    def 'requests agreeing on fieldType and initializer for one dedup key are not a conflict, even as distinct instances'() {
        def requestA = MemberRequest.field(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'yyyy-MM-dd'), 'fmt')
        def requestB = MemberRequest.field(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'yyyy-MM-dd'), 'fmt')
        def a = target('a')
        def b = target('b')
        operation(a, [requestA], 'opA')
        operation(b, [requestB], 'opB')
        def root = target('')
        graph.markReturnRoot(root)
        assemble(root, [a, b])
        def plan = ExtractedPlan.extract(graph)

        when:
        def memberPlan = memberPlanFactory.forMapper(graph, plan, ctx)

        then:
        ctx.diagnostics.empty

        expect:
        memberPlan.fields().size() == 1
    }

    def 'requests disagreeing on initializer for one dedup key report a permanent conflict naming the key, both initializers and both requesting operations'() {
        def requestA = MemberRequest.field(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'yyyy-MM-dd'), 'fmt')
        def requestB = MemberRequest.field(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'dd.MM.yyyy'), 'fmt')
        def a = target('a')
        def b = target('b')
        operation(a, [requestA], 'opA')
        operation(b, [requestB], 'opB')
        def root = target('')
        graph.markReturnRoot(root)
        assemble(root, [a, b])
        def plan = ExtractedPlan.extract(graph)

        when:
        memberPlanFactory.forMapper(graph, plan, ctx)

        then:
        ctx.diagnostics.size() == 1

        expect:
        with(ctx.diagnostics[0]) {
            permanent
            message.contains('fmt') && message.contains('yyyy-MM-dd') && message.contains('dd.MM.yyyy')
                    && message.contains('opA') && message.contains('opB')
        }
    }

    def 'requests disagreeing on field type for one dedup key report a permanent conflict'() {
        def requestA = MemberRequest.field(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'yyyy-MM-dd'), 'fmt')
        def other = ClassName.get('java.lang', 'String')
        def requestB = MemberRequest.field(other, CodeBlock.of('$S', 'x'), 'fmt')
        def a = target('a')
        def b = target('b')
        operation(a, [requestA], 'opA')
        operation(b, [requestB], 'opB')
        def root = target('')
        graph.markReturnRoot(root)
        assemble(root, [a, b])
        def plan = ExtractedPlan.extract(graph)

        when:
        memberPlanFactory.forMapper(graph, plan, ctx)

        then:
        ctx.diagnostics.size() == 1

        expect:
        with(ctx.diagnostics[0]) {
            permanent
            message.contains('fmt')
        }
    }

    def 'a conflicting request from an operation outside the winning plan is ignored'() {
        def winner = MemberRequest.field(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'yyyy-MM-dd'), 'fmt')
        def loser = MemberRequest.field(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'dd.MM.yyyy'), 'fmt')
        def root = target('')
        graph.markReturnRoot(root)
        operation(root, [winner], 'cheap', 1)
        operation(root, [loser], 'expensive', 100)
        def plan = ExtractedPlan.extract(graph)

        when:
        def memberPlan = memberPlanFactory.forMapper(graph, plan, ctx)

        then:
        ctx.diagnostics.empty

        expect:
        memberPlan.fields().size() == 1
    }

    private static HelperStyle style(final MemberVisibility visibility, final boolean isStatic) {
        new HelperStyle(visibility, isStatic)
    }


    private Value target(final String slot) {
        graph.valueFor(scope, new TargetLocation(TargetPath.of(slot)), STRING, Nullability.NON_NULL)
    }

    private void operation(final Value out, final List<MemberRequest> memberRequests, final String label = 'op', final int weight = 1) {
        graph.apply(new AddOperation(label, OP, weight, false, [], av(out), Optional.empty(), [] as Set, memberRequests))
    }

    private void assemble(final Value out, final List<Value> portSources) {
        def ports = (0..<portSources.size()).collect { i ->
            new io.github.joke.percolate.processor.internal.graph.PortBinding(
                    new io.github.joke.percolate.spi.Port('p' + i, portSources[i].type.get(), portSources[i].nullness.get()),
                    av(portSources[i]))
        }
        graph.apply(new AddOperation('assemble', OP, 1, false, ports, av(out), Optional.empty(), [] as Set, []))
    }

    private AddValue av(final Value value) {
        new AddValue(value.scope, value.loc, value.type.get(), value.nullness.get())
    }
}
