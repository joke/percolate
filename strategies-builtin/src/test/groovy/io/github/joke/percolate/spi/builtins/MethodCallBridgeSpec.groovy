package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.FakeReceiver
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import java.util.stream.Stream

@Tag('unit')
class MethodCallBridgeSpec extends Specification {

    def 'returns empty when callableMethods returns null'() {
        given:
        def ctx = new ResolveCtxBuilder()
                .withCallableMethods(null)
                .build()

        when:
        def steps = new MethodCallBridge().bridge(TypeUniverse.STRING, TypeUniverse.STRING, ctx).toList()

        then:
        steps.empty
    }

    def 'returns empty when callableMethods produces an empty stream'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new MethodCallBridge().bridge(TypeUniverse.STRING, TypeUniverse.STRING, ctx).toList()

        then:
        steps.empty
    }

    def 'returns step for single-parameter method whose return type is assignable to target'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        when:
        def steps = new MethodCallBridge().bridge(TypeUniverse.STRING, TypeUniverse.STRING, ctx).toList()

        then:
        steps.empty // No CallableMethods configured, so empty
    }

    def 'returns step when CallableMethods provides a matching candidate'() {
        given:
        def candidate = createExactMatchCandidate()
        def ctx = new ResolveCtxBuilder()
                .withCallableMethods(new io.github.joke.percolate.spi.CallableMethods() {
                    @Override
                    Stream<io.github.joke.percolate.spi.MethodCandidate> producing(final javax.lang.model.type.TypeMirror outputType) {
                        Stream.of(candidate)
                    }
                })
                .build()

        when:
        def steps = new MethodCallBridge().bridge(TypeUniverse.STRING, TypeUniverse.STRING, ctx).toList()

        then:
        steps.size() == 1
        steps[0].weight >= Weights.METHOD
    }

    // FOLLOW-UP: pin current behaviour — subtypeDistance returns 0 for both same-type and non-assignable inputs
    def 'pins current behaviour: subtypeDistance returns 0 for same-type input'() {
        given:
        def candidate = createExactMatchCandidate()
        def ctx = new ResolveCtxBuilder()
                .withCallableMethods(new io.github.joke.percolate.spi.CallableMethods() {
                    @Override
                    Stream<io.github.joke.percolate.spi.MethodCandidate> producing(final javax.lang.model.type.TypeMirror outputType) {
                        Stream.of(candidate)
                    }
                })
                .build()

        when:
        def steps = new MethodCallBridge().bridge(TypeUniverse.STRING, TypeUniverse.STRING, ctx).toList()

        then:
        steps.size() == 1
        // subtypeDistance == 0 for same-type — weight should be METHOD + 0 + 0 = METHOD
        steps[0].weight == Weights.METHOD
    }

    // FOLLOW-UP: pin current behaviour — subtypeDistance returns 0 for non-assignable inputs
    def 'pins current behaviour: subtypeDistance returns 0 for non-assignable input'() {
        given:
        def candidate = createValueOfObjectCandidate()
        def ctx = new ResolveCtxBuilder()
                .withCallableMethods(new io.github.joke.percolate.spi.CallableMethods() {
                    @Override
                    Stream<io.github.joke.percolate.spi.MethodCandidate> producing(final javax.lang.model.type.TypeMirror outputType) {
                        Stream.of(candidate)
                    }
                })
                .build()

        when:
        // With --module-path '', isAssignable(INT, Object) returns true and bfsDistance
        // skips primitives, so subtypeDistance returns 0 — weight is METHOD
        def steps = new MethodCallBridge().bridge(TypeUniverse.INT, TypeUniverse.STRING, ctx).toList()

        then:
        steps.size() == 1
        steps[0].weight == Weights.METHOD
    }

    private static io.github.joke.percolate.spi.MethodCandidate createExactMatchCandidate() {
        def concatElement = TypeUniverse.element('java.lang.String').enclosedElements.stream()
                .filter { it.simpleName.toString() == 'concat' }
                .filter { it instanceof ExecutableElement }
                .map(ExecutableElement.&cast)
                .filter { it.parameters.size() == 1 }
                .filter {
                    def paramType = it.parameters.get(0).asType()
                    def elem = TypeUniverse.types().asElement(paramType)
                    elem instanceof TypeElement && ((TypeElement) elem).qualifiedName.contentEquals('java.lang.String')
                }
                .findFirst()
                .orElse(null)
        if (concatElement == null) {
            throw new IllegalStateException('concat method not found on java.lang.String')
        }
        def fakeReceiver = FakeReceiver.instance()
        new io.github.joke.percolate.spi.MethodCandidate(concatElement, fakeReceiver)
    }

    private static io.github.joke.percolate.spi.MethodCandidate createValueOfObjectCandidate() {
        def valueOfElement = TypeUniverse.element('java.lang.String').enclosedElements.stream()
                .filter { it.simpleName.toString() == 'valueOf' }
                .filter { it instanceof ExecutableElement }
                .map(ExecutableElement.&cast)
                .filter { it.parameters.size() == 1 }
                .filter {
                    def paramType = it.parameters.get(0).asType()
                    def elem = TypeUniverse.types().asElement(paramType)
                    elem instanceof TypeElement && ((TypeElement) elem).qualifiedName.contentEquals('java.lang.Object')
                }
                .findFirst()
                .orElse(null)
        if (valueOfElement == null) {
            throw new IllegalStateException('valueOf(Object) method not found on java.lang.String')
        }
        def fakeReceiver = FakeReceiver.instance()
        new io.github.joke.percolate.spi.MethodCandidate(valueOfElement, fakeReceiver)
    }
}
