package io.github.joke.percolate.processor.internal.stages.validate

import io.github.joke.percolate.Ambient
import io.github.joke.percolate.processor.Diagnostics
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.internal.graph.TargetPath
import io.github.joke.percolate.processor.model.MapperShape
import io.github.joke.percolate.processor.nullability.NullabilityResolver
import io.github.joke.percolate.spi.CallableMethods
import io.github.joke.percolate.spi.MethodCandidate
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.ThisReceiver
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * {@link ValidateAmbientBindingsStage} unit-tested mock-only: it independently re-derives the three ambient
 * diagnostics (duplicate key, unbound key, key/type mismatch) rather than inspecting landed operations — see the
 * class javadoc and design.md's implementation note. Every diagnostic positions at the mapper type (the only
 * positioning that reliably suppresses {@link RealisationDiagnosticsStage}'s generic message across both
 * directly-declared and inherited candidates).
 */
@Tag('unit')
class ValidateAmbientBindingsStageSpec extends Specification {

    Diagnostics diagnostics = Mock()
    NullabilityResolver resolver = Mock()
    ValidateAmbientBindingsStage stage = new ValidateAmbientBindingsStage(diagnostics, resolver)

    TypeElement mapperType = Mock()
    TypeMirror targetType = Mock()
    TypeMirror personType = Mock()
    TypeMirror customerType = Mock()

    def 'reports nothing when the mapper already carries errors'() {
        diagnostics.hasErrorsFor(mapperType) >> true
        def method = methodWithParams(ambientParam('a', targetType, 'ctx'), ambientParam('b', targetType, 'ctx'))

        when:
        stage.run(context(new MapperGraph(), new MapperShape(mapperType, [method]), emptyCallableMethods()))

        then:
        0 * diagnostics.error(*_)
    }

    def 'reports nothing when the graph is absent'() {
        expect:
        new ValidateAmbientBindingsStage(diagnostics, resolver).run(new MapperContext(mapperType))
    }

    def 'reports a duplicate ambient key on an abstract method, positioned at the mapper type'() {
        def method = methodWithParams(ambientParam('a', targetType, 'ctx'), ambientParam('b', targetType, 'ctx'))
        diagnostics.hasErrorsFor(mapperType) >> false

        when:
        stage.run(context(new MapperGraph(), new MapperShape(mapperType, [method]), emptyCallableMethods()))

        then:
        1 * diagnostics.error(mapperType) { it.contains("duplicate @Ambient key 'ctx'") && it.contains('map') }
    }

    def 'reports an unbound ambient key for a reachable candidate that declares one'() {
        def scope = new MethodScope(methodWithParams())
        def graph = new MapperGraph()
        graph.apply(new AddValue(scope, root(), targetType, Nullability.NON_NULL))
        def candidateMethod = methodNamed('mapPrice', ambientParam('order', targetType, ''))
        def callableMethods = candidateProducing(targetType, candidateMethod)
        diagnostics.hasErrorsFor(mapperType) >> false

        when:
        stage.run(context(graph, new MapperShape(mapperType, []), callableMethods))

        then:
        1 * diagnostics.error(mapperType) {
            it.contains("unbound @Ambient key 'order'") && it.contains('mapPrice')
        }
    }

    def 'reports a key/type mismatch when the binding resolves but the type is not assignable'() {
        def providerParam = ambientParam('simon', personType, '')
        def scope = new MethodScope(methodWithParams(providerParam))
        resolver.resolve(personType, providerParam) >> Nullability.NON_NULL
        def graph = new MapperGraph()
        graph.apply(new AddValue(scope, root(), targetType, Nullability.NON_NULL))
        def consumingParam = ambientParam('simon', customerType, '')
        def candidateMethod = methodNamed('mapAddress', consumingParam)
        def callableMethods = candidateProducing(targetType, candidateMethod)
        def resolveCtx = Mock(ResolveCtx) {
            isAssignable(personType, customerType) >> false
        }
        diagnostics.hasErrorsFor(mapperType) >> false

        when:
        stage.run(context(graph, new MapperShape(mapperType, []), callableMethods, resolveCtx))

        then:
        1 * diagnostics.error(mapperType) {
            it.contains("@Ambient key 'simon' is bound to") && it.contains('mapAddress')
        }
    }

    def 'reports nothing when the ambient binding resolves and the type verifies'() {
        def providerParam = ambientParam('order', targetType, '')
        def scope = new MethodScope(methodWithParams(providerParam))
        resolver.resolve(targetType, providerParam) >> Nullability.NON_NULL
        def graph = new MapperGraph()
        graph.apply(new AddValue(scope, root(), targetType, Nullability.NON_NULL))
        def candidateMethod = methodWithParams(ambientParam('order', targetType, ''))
        def callableMethods = candidateProducing(targetType, candidateMethod)
        def resolveCtx = Mock(ResolveCtx) {
            isAssignable(targetType, targetType) >> true
        }
        diagnostics.hasErrorsFor(mapperType) >> false

        when:
        stage.run(context(graph, new MapperShape(mapperType, []), callableMethods, resolveCtx))

        then:
        0 * diagnostics.error(*_)
    }

    def 'checks each method/parameter/scope combination once even when reached from multiple demanded values'() {
        def scope = new MethodScope(methodWithParams())
        def graph = new MapperGraph()
        graph.apply(new AddValue(scope, root(), targetType, Nullability.NON_NULL))
        graph.apply(new AddValue(scope, new TargetLocation(new TargetPath(['other'])), targetType, Nullability.NON_NULL))
        def candidateMethod = methodWithParams(ambientParam('order', targetType, ''))
        def callableMethods = candidateProducing(targetType, candidateMethod)
        diagnostics.hasErrorsFor(mapperType) >> false

        when:
        stage.run(context(graph, new MapperShape(mapperType, []), callableMethods))

        then:
        1 * diagnostics.error(mapperType, _)
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private MapperContext context(final MapperGraph graph, final MapperShape shape, final CallableMethods callableMethods) {
        context(graph, shape, callableMethods, Stub(ResolveCtx))
    }

    private MapperContext context(
            final MapperGraph graph, final MapperShape shape, final CallableMethods callableMethods,
            final ResolveCtx resolveCtx) {
        def ctx = new MapperContext(mapperType)
        ctx.graph = graph
        ctx.shape = shape
        ctx.callableMethods = callableMethods
        ctx.resolveCtx = resolveCtx
        ctx
    }

    private CallableMethods emptyCallableMethods() {
        Mock(CallableMethods) {
            producing(_) >> Stream.empty()
        }
    }

    private CallableMethods candidateProducing(final TypeMirror type, final ExecutableElement method) {
        Mock(CallableMethods) {
            // A closure answer (not a canned Stream instance) so a second call gets a fresh, unconsumed stream.
            producing(type) >> { Stream.of(new MethodCandidate(method, ThisReceiver.INSTANCE)) }
        }
    }

    private ExecutableElement methodWithParams(final VariableElement... params) {
        methodNamed('map', params)
    }

    private ExecutableElement methodNamed(final String methodName, final VariableElement... params) {
        Mock(ExecutableElement) {
            getParameters() >> (params as List)
            getSimpleName() >> name(methodName)
        }
    }

    private VariableElement ambientParam(final String paramName, final TypeMirror type, final String keyOverride) {
        Mock(VariableElement) {
            getAnnotation(Ambient) >> Mock(Ambient) { value() >> keyOverride }
            getSimpleName() >> name(paramName)
            asType() >> type
        }
    }

    private TargetLocation root() {
        new TargetLocation(new TargetPath([]))
    }

    private Name name(final String value) {
        Stub(Name) { toString() >> value }
    }
}
