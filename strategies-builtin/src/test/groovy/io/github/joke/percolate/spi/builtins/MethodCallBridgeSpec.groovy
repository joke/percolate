package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import io.github.joke.percolate.spi.builtins.test.FakeReceiver
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import java.util.stream.Stream

@Tag('unit')
class MethodCallBridgeSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()
    @Shared def types = javac.types()

    def 'returns empty when callableMethods is null'() {
        given:
        def ctx = new ResolveCtxBuilder(javac)
                .withCallableMethods(null)
                .build()

        expect:
        new MethodCallBridge().expand(Demands.forTarget(javac.STRING), ctx).toList().empty
    }

    def 'returns empty when callableMethods produces an empty stream'() {
        given:
        def ctx = new ResolveCtxBuilder(javac).build()

        expect:
        new MethodCallBridge().expand(Demands.forTarget(javac.STRING), ctx).toList().empty
    }

    def 'emits a one-port call operation when CallableMethods provides a matching candidate'() {
        given:
        def candidate = createExactMatchCandidate()
        def ctx = candidateCtx(candidate)

        when:
        def specs = new MethodCallBridge().expand(Demands.forTarget(javac.STRING), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.ports.size() == 1
        spec.weight >= Weights.METHOD
        types.isSameType(spec.outputType, javac.STRING)
        spec.outputNullness == Nullability.NON_NULL
    }

    def 'pins current behaviour: subtypeDistance returns 0 for a same-type return'() {
        given:
        def candidate = createExactMatchCandidate()
        def ctx = candidateCtx(candidate)

        when:
        def specs = new MethodCallBridge().expand(Demands.forTarget(javac.STRING), ctx).toList()

        then:
        specs.size() == 1
        // returnType String == target String → distance 0 → weight is METHOD + 0
        specs[0].weight == Weights.METHOD
    }

    def 'pins current behaviour: subtypeDistance returns 0 for a non-assignable parameter'() {
        given:
        def candidate = createValueOfObjectCandidate()
        def ctx = candidateCtx(candidate)

        when:
        // valueOf(Object) returns String; the parameter type is irrelevant to weight, which is driven only by
        // the return→target distance (0 here), so weight is METHOD.
        def specs = new MethodCallBridge().expand(Demands.forTarget(javac.STRING), ctx).toList()

        then:
        specs.size() == 1
        specs[0].weight == Weights.METHOD
    }

    private io.github.joke.percolate.spi.ResolveCtx candidateCtx(final candidate) {
        new ResolveCtxBuilder(javac)
                .withCallableMethods(new io.github.joke.percolate.spi.CallableMethods() {
                    @Override
                    Stream<io.github.joke.percolate.spi.MethodCandidate> producing(final javax.lang.model.type.TypeMirror outputType) {
                        Stream.of(candidate)
                    }
                })
                .build()
    }

    private io.github.joke.percolate.spi.MethodCandidate createExactMatchCandidate() {
        def concatElement = javac.element('java.lang.String').enclosedElements.stream()
                .filter { it.simpleName.toString() == 'concat' }
                .filter { it instanceof ExecutableElement }
                .map(ExecutableElement.&cast)
                .filter { it.parameters.size() == 1 }
                .filter {
                    def paramType = it.parameters.get(0).asType()
                    def elem = javac.types().asElement(paramType)
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

    private io.github.joke.percolate.spi.MethodCandidate createValueOfObjectCandidate() {
        def valueOfElement = javac.element('java.lang.String').enclosedElements.stream()
                .filter { it.simpleName.toString() == 'valueOf' }
                .filter { it instanceof ExecutableElement }
                .map(ExecutableElement.&cast)
                .filter { it.parameters.size() == 1 }
                .filter {
                    def paramType = it.parameters.get(0).asType()
                    def elem = javac.types().asElement(paramType)
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
