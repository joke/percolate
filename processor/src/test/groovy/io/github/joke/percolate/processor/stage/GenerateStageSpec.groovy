package io.github.joke.percolate.processor.stage

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.TransformEdge
import io.github.joke.percolate.processor.model.ConstructorParamAccessor
import io.github.joke.percolate.processor.model.FieldReadAccessor
import io.github.joke.percolate.processor.model.GetterAccessor
import io.github.joke.percolate.processor.model.WriteAccessor
import io.github.joke.percolate.processor.spi.TypeTransformStrategy
import io.github.joke.percolate.processor.transform.CodeTemplate
import io.github.joke.percolate.processor.transform.TransformProposal
import io.github.joke.percolate.processor.transform.ResolvedMapping
import io.github.joke.percolate.processor.transform.TransformResolution
import org.jgrapht.GraphPath
import org.jgrapht.graph.DefaultDirectedGraph
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.Filer
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
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
        final mapping = resolvedMapping([getterAccessor(getter)], constructorWriter('prop', 0), edges)

        expect:
        stage.generateValueExpression(mapping, 'source').toString() == expected

        where:
        getter       | edges                                   || expected
        'getMembers' | [stream(), streamMap(), collectToSet()] || 'source.getMembers().stream().map(e -> mapPerson(e)).collect(java.util.stream.Collectors.toSet())'
        'getPerson'  | [optionalMap()]                         || 'source.getPerson().map(e -> mapPerson(e))'
        'getName'    | [identity()]                            || 'source.getName()'
        'getAddress' | [methodCall('mapAddress')]              || 'mapAddress(source.getAddress())'
        'getValue'   | [optionalWrap()]                        || 'java.util.Optional.of(source.getValue())'
        'getValue'   | [optionalUnwrap()]                      || 'source.getValue().get()'
    }

    def 'generates field read expression for FieldReadAccessor source'() {
        given:
        final accessor = new FieldReadAccessor('firstName', Stub(TypeMirror), Stub(VariableElement))
        final mapping = resolvedMapping([accessor], constructorWriter('firstName', 0), [identity()])

        expect:
        stage.generateValueExpression(mapping, 'source').toString() == 'source.firstName'
    }

    def 'generates chained read expression for nested accessor chain'() {
        given:
        final addressGetter = getterAccessor('getAddress')
        final streetGetter = getterAccessor('getStreet')
        final mapping = resolvedMapping([addressGetter, streetGetter], constructorWriter('street', 0), [identity()])

        expect:
        stage.generateValueExpression(mapping, 'src').toString() == 'src.getAddress().getStreet()'
    }

    def 'applies transform edges sequentially after building full chain expression'() {
        given:
        final streetGetter = getterAccessor('getStreet')
        final addressGetter = getterAccessor('getAddress')
        final mapping = resolvedMapping([addressGetter, streetGetter], constructorWriter('out', 0),
                [methodCall('normalize')])

        expect:
        stage.generateValueExpression(mapping, 'src').toString() == 'normalize(src.getAddress().getStreet())'
    }

    private GetterAccessor getterAccessor(final String methodName) {
        final method = Stub(ExecutableElement) {
            getSimpleName() >> Stub(Name) { toString() >> methodName }
        }
        return new GetterAccessor(methodName, Stub(TypeMirror), method)
    }

    private WriteAccessor constructorWriter(final String name, final int paramIndex) {
        return new ConstructorParamAccessor(name, Stub(TypeMirror), Stub(ExecutableElement), paramIndex)
    }

    private ResolvedMapping resolvedMapping(
            final List chain, final WriteAccessor writer, final List<TransformEdge> edges) {
        final path = Stub(GraphPath) { getEdgeList() >> edges }
        final resolution = new TransformResolution(new DefaultDirectedGraph(TransformEdge), path)
        return new ResolvedMapping(chain, 'prop', writer, writer.name, resolution, null, [:], '', null)
    }

    private TransformEdge edge(final CodeTemplate template) {
        final edge = new TransformEdge(Stub(TypeTransformStrategy), Stub(TransformProposal))
        edge.resolveTemplate(template)
        return edge
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

    private TransformEdge methodCall(final String method) {
        return edge({ CodeBlock input -> CodeBlock.of(method + '($L)', input) })
    }
}
