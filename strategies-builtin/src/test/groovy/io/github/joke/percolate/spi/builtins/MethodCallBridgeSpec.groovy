package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Intent
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.FakeReceiver
import io.github.joke.percolate.spi.builtins.test.Frontiers
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import java.util.stream.Stream

@Tag('unit')
class MethodCallBridgeSpec extends Specification {

    def 'returns empty when callableMethods is null'() {
        given:
        def ctx = new ResolveCtxBuilder()
                .withCallableMethods(null)
                .build()

        expect:
        new MethodCallBridge().expand(Frontiers.forTarget(TypeUniverse.STRING), ctx).toList().empty
    }

    def 'returns empty when callableMethods produces an empty stream'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        expect:
        new MethodCallBridge().expand(Frontiers.forTarget(TypeUniverse.STRING), ctx).toList().empty
    }

    def 'emits a one-slot BOUNDARY step when CallableMethods provides a matching candidate'() {
        given:
        def candidate = createExactMatchCandidate()
        def ctx = candidateCtx(candidate)

        when:
        def steps = new MethodCallBridge().expand(Frontiers.forTarget(TypeUniverse.STRING), ctx).toList()

        then:
        steps.size() == 1
        steps[0].intent == Intent.BOUNDARY
        steps[0].inputs.size() == 1
        steps[0].weight >= Weights.METHOD
    }

    def 'pins current behaviour: subtypeDistance returns 0 for a same-type return'() {
        given:
        def candidate = createExactMatchCandidate()
        def ctx = candidateCtx(candidate)

        when:
        def steps = new MethodCallBridge().expand(Frontiers.forTarget(TypeUniverse.STRING), ctx).toList()

        then:
        steps.size() == 1
        // returnType String == target String → distance 0 → weight is METHOD + 0
        steps[0].weight == Weights.METHOD
    }

    def 'pins current behaviour: subtypeDistance returns 0 for a non-assignable parameter'() {
        given:
        def candidate = createValueOfObjectCandidate()
        def ctx = candidateCtx(candidate)

        when:
        // valueOf(Object) returns String; the parameter type is irrelevant to weight, which is driven only by
        // the return→target distance (0 here), so weight is METHOD.
        def steps = new MethodCallBridge().expand(Frontiers.forTarget(TypeUniverse.STRING), ctx).toList()

        then:
        steps.size() == 1
        steps[0].weight == Weights.METHOD
    }

    private static io.github.joke.percolate.spi.ResolveCtx candidateCtx(final candidate) {
        new ResolveCtxBuilder()
                .withCallableMethods(new io.github.joke.percolate.spi.CallableMethods() {
                    @Override
                    Stream<io.github.joke.percolate.spi.MethodCandidate> producing(final javax.lang.model.type.TypeMirror outputType) {
                        Stream.of(candidate)
                    }
                })
                .build()
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
