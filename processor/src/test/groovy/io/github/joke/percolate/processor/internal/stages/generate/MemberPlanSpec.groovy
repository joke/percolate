package io.github.joke.percolate.processor.internal.stages.generate

import io.github.joke.percolate.lib.javapoet.ClassName
import io.github.joke.percolate.lib.javapoet.CodeBlock
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
import javax.lang.model.element.Name
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

    @Shared TypeMirror STRING = Mock()

    def method = Mock(ExecutableElement) {
        getSimpleName() >> Stub(Name) { toString() >> 'map' }
        getParameters() >> []
    }
    MethodScope scope = new MethodScope(method)
    MapperGraph graph = new MapperGraph()

    def 'two operations sharing a dedup key resolve to exactly one field'() {
        def request = new MemberRequest(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'yyyy-MM-dd'), 'fmt-yyyy-MM-dd')
        def a = target('a')
        def b = target('b')
        operation(a, [request])
        operation(b, [request])
        def root = target('')
        graph.markReturnRoot(root)
        assemble(root, [a, b])
        def plan = ExtractedPlan.extract(graph)

        expect:
        MemberPlan.forMapper(graph, plan).fields().size() == 1
    }

    def 'distinct dedup keys resolve to distinct field names'() {
        def requestA = new MemberRequest(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'yyyy-MM-dd'), 'fmt-yyyy-MM-dd')
        def requestB = new MemberRequest(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'dd.MM.yyyy'), 'fmt-dd.MM.yyyy')
        def a = target('a')
        def b = target('b')
        operation(a, [requestA])
        operation(b, [requestB])
        def root = target('')
        graph.markReturnRoot(root)
        assemble(root, [a, b])
        def plan = ExtractedPlan.extract(graph)
        def memberPlan = MemberPlan.forMapper(graph, plan)

        expect:
        memberPlan.reference('fmt-yyyy-MM-dd').toString() != memberPlan.reference('fmt-dd.MM.yyyy').toString()
    }

    def 'a mapper whose operations request no member declares no fields'() {
        def root = target('')
        graph.markReturnRoot(root)
        operation(root, [])
        def plan = ExtractedPlan.extract(graph)

        expect:
        MemberPlan.forMapper(graph, plan).fields().empty
    }

    def 'each distinct member is emitted once as a field, initialized with the requested initializer'() {
        def request = new MemberRequest(FORMATTER, CodeBlock.of('$T.ofPattern($S)', FORMATTER, 'yyyy-MM-dd'), 'fmt-yyyy-MM-dd')
        def a = target('a')
        operation(a, [request])
        def root = target('')
        graph.markReturnRoot(root)
        assemble(root, [a])
        def plan = ExtractedPlan.extract(graph)

        when:
        def fields = MemberPlan.forMapper(graph, plan).fields()

        then:
        fields.size() == 1
        fields[0].type == FORMATTER
        fields[0].initializer.toString().contains('DateTimeFormatter.ofPattern("yyyy-MM-dd")')
    }

    def 'referencing an unregistered dedup key fails fast'() {
        def root = target('')
        graph.markReturnRoot(root)
        operation(root, [])
        def plan = ExtractedPlan.extract(graph)

        when:
        MemberPlan.forMapper(graph, plan).reference('unknown')

        then:
        def error = thrown(IllegalStateException)

        expect:
        error.message.contains('unknown')
    }

    def 'memberBase names the field after a ClassName\'s lower-camel simple name'() {
        expect:
        MemberPlan.memberBase(FORMATTER) == 'dateTimeFormatter'
    }

    def 'memberBase falls back to "member" for a non-ClassName field type (e.g. a primitive)'() {
        expect:
        MemberPlan.memberBase(io.github.joke.percolate.lib.javapoet.TypeName.INT) == 'member'
    }

    private Value target(final String slot) {
        graph.valueFor(scope, new TargetLocation(TargetPath.of(slot)), STRING, Nullability.NON_NULL)
    }

    private void operation(final Value out, final List<MemberRequest> memberRequests) {
        graph.apply(new AddOperation('op', OP, 1, false, [], av(out), Optional.empty(), [] as Set, memberRequests))
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
