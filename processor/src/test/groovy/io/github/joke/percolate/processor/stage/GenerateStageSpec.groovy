package io.github.joke.percolate.processor.stage

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.SourcePropertyNode
import io.github.joke.percolate.processor.graph.TargetPropertyNode
import io.github.joke.percolate.processor.graph.TransformEdge
import io.github.joke.percolate.processor.model.ConstructorParamAccessor
import io.github.joke.percolate.processor.model.FieldReadAccessor
import io.github.joke.percolate.processor.model.GetterAccessor
import io.github.joke.percolate.processor.spi.TypeTransformStrategy
import io.github.joke.percolate.processor.transform.CodeTemplate
import io.github.joke.percolate.processor.transform.ResolvedMapping
import org.jgrapht.GraphPath
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.Filer
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import java.util.stream.Collectors

@Tag('unit')
class GenerateStageSpec extends Specification {

    final filer = Stub(Filer)
    final stage = new GenerateStage(filer)

    def 'stage can be instantiated with Filer'() {
        expect:
        stage != null
    }

    def 'generates value expression by composing code templates from path edges'() {
        given:
        final mapping = resolvedMapping(getterSource(getter), constructorTarget('prop', 0), edges)

        expect:
        stage.generateValueExpression(mapping, 'source').toString() == expected

        where:
        getter       | edges                                    || expected
        'getMembers' | [stream(), streamMap(), collectToSet()]  || 'source.getMembers().stream().map(e -> mapPerson(e)).collect(java.util.stream.Collectors.toSet())'
        'getPerson'  | [optionalMap()]                          || 'source.getPerson().map(e -> mapPerson(e))'
        'getName'    | [identity()]                             || 'source.getName()'
        'getAddress' | [methodCall('mapAddress')]               || 'mapAddress(source.getAddress())'
        'getValue'   | [optionalWrap()]                         || 'java.util.Optional.of(source.getValue())'
        'getValue'   | [optionalUnwrap()]                       || 'source.getValue().get()'
    }

    def 'generates field read expression for FieldReadAccessor source'() {
        given:
        final accessor = new FieldReadAccessor('firstName', Stub(TypeMirror), Stub(VariableElement))
        final source = new SourcePropertyNode('firstName', Stub(TypeMirror), accessor)
        final mapping = resolvedMapping(source, constructorTarget('firstName', 0), [identity()])

        expect:
        stage.generateValueExpression(mapping, 'source').toString() == 'source.firstName'
    }

    private SourcePropertyNode getterSource(String methodName) {
        final method = Stub(ExecutableElement) {
            getSimpleName() >> Stub(javax.lang.model.element.Name) { toString() >> methodName }
        }
        return new SourcePropertyNode(methodName, Stub(TypeMirror), new GetterAccessor(methodName, Stub(TypeMirror), method))
    }

    private TargetPropertyNode constructorTarget(String name, int paramIndex) {
        return new TargetPropertyNode(name, Stub(TypeMirror), new ConstructorParamAccessor(name, Stub(TypeMirror), Stub(ExecutableElement), paramIndex))
    }

    private ResolvedMapping resolvedMapping(SourcePropertyNode source, TargetPropertyNode target, List<TransformEdge> edges) {
        return new ResolvedMapping(source, target, Stub(GraphPath) { getEdgeList() >> edges })
    }

    private TransformEdge edge(CodeTemplate template) {
        return new TransformEdge(Stub(TypeTransformStrategy), template)
    }

    private TransformEdge stream() {
        return edge({ CodeBlock input -> CodeBlock.of('$L.stream()', input) })
    }

    private TransformEdge streamMap() {
        return edge({ CodeBlock input -> CodeBlock.of('$L.map(e -> mapPerson(e))', input) })
    }

    private TransformEdge collectToSet() {
        return edge({ CodeBlock input -> CodeBlock.of('$L.collect($T.toSet())', input, Collectors) })
    }

    private TransformEdge optionalMap() {
        return edge({ CodeBlock input -> CodeBlock.of('$L.map(e -> mapPerson(e))', input) })
    }

    private TransformEdge identity() {
        return edge({ CodeBlock input -> input })
    }

    private TransformEdge optionalWrap() {
        return edge({ CodeBlock input -> CodeBlock.of('$T.of($L)', Optional, input) })
    }

    private TransformEdge optionalUnwrap() {
        return edge({ CodeBlock input -> CodeBlock.of('$L.get()', input) })
    }

    private TransformEdge methodCall(String method) {
        return edge({ CodeBlock input -> CodeBlock.of(method + '($L)', input) })
    }
}
