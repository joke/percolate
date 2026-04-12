package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.ProcessorOptions
import io.github.joke.percolate.processor.graph.TransformEdge
import io.github.joke.percolate.processor.graph.TypeNode
import io.github.joke.percolate.processor.model.MappingMethodModel
import io.github.joke.percolate.processor.spi.DirectAssignableStrategy
import io.github.joke.percolate.processor.transform.ResolvedMapping
import io.github.joke.percolate.processor.transform.ResolvedModel
import io.github.joke.percolate.processor.transform.TransformProposal
import io.github.joke.percolate.processor.transform.TransformResolution
import org.jgrapht.alg.shortestpath.BFSShortestPath
import org.jgrapht.graph.DefaultDirectedGraph
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.tools.FileObject

import static javax.tools.Diagnostic.Kind.WARNING

@Tag('unit')
class DumpTransformGraphStageSpec extends Specification {

    Filer filer = Mock()
    Messager messager = Mock()
    DumpTransformGraphStage stage = new DumpTransformGraphStage(new ProcessorOptions(true, 'dot'), filer, messager)

    def 'is a no-op when debug graphs are disabled'() {
        given:
        final stage = new DumpTransformGraphStage(new ProcessorOptions(false, 'dot'), filer, messager)

        when:
        stage.execute(resolvedModelWithPath())

        then:
        0 * filer._
    }

    def 'marks winning path edges as bold in DOT output'() {
        given:
        final writer = new StringWriter()
        filer.createResource(_, _, _) >> Stub(FileObject) { openWriter() >> writer }

        when:
        stage.execute(resolvedModelWithPath())

        then:
        writer.toString().contains('style="bold"')
    }

    def 'winning edges use winning=true attribute for non-DOT formats'() {
        given:
        final stage = new DumpTransformGraphStage(new ProcessorOptions(true, 'graphml'), filer, messager)
        final writer = new StringWriter()
        filer.createResource(_, _, _) >> Stub(FileObject) { openWriter() >> writer }

        when:
        stage.execute(resolvedModelWithPath())

        then:
        writer.toString().contains('winning')
        !writer.toString().contains('style="bold"')
    }

    def 'non-winning exploration edges are not marked bold'() {
        given:
        final writer = new StringWriter()
        filer.createResource(_, _, _) >> Stub(FileObject) { openWriter() >> writer }

        when:
        stage.execute(resolvedModelWithUnresolvedMapping())

        then:
        !writer.toString().contains('style="bold"')
    }

    def 'merges exploration graphs from multiple mappings deduplicating shared node labels'() {
        given:
        final writer = new StringWriter()
        filer.createResource(_, _, _) >> Stub(FileObject) { openWriter() >> writer }

        when:
        stage.execute(resolvedModelWithSharedNodes())

        then:
        // 'String' node label appears exactly once despite being in both exploration graphs
        writer.toString().count('label="String"') == 1
    }

    def 'skips method when merged exploration graph is empty'() {
        given:
        final method = minimalMethod()
        final mapping = new ResolvedMapping([], 'src', null, 'name', null, null, [:])
        final resolvedModel = resolvedModel(method, [mapping])

        when:
        stage.execute(resolvedModel)

        then:
        0 * filer._
    }

    def 'catches IOException from filer and logs warning without aborting'() {
        given:
        filer.createResource(_, _, _) >> { throw new IOException('disk full') }

        when:
        stage.execute(resolvedModelWithPath())

        then:
        1 * messager.printMessage(WARNING, { it.contains('TestMapper_map_transform') })
    }

    private ResolvedModel resolvedModelWithPath() {
        final sourceNode = new TypeNode(Stub(TypeMirror) { toString() >> 'Source' }, 'Source')
        final targetNode = new TypeNode(Stub(TypeMirror) { toString() >> 'Target' }, 'Target')
        final graph = new DefaultDirectedGraph<TypeNode, TransformEdge>(TransformEdge)
        graph.addVertex(sourceNode)
        graph.addVertex(targetNode)
        final edge = new TransformEdge(new DirectAssignableStrategy(), Stub(TransformProposal))
        edge.resolveTemplate({ input -> input })
        graph.addEdge(sourceNode, targetNode, edge)
        final path = new BFSShortestPath<>(graph).getPath(sourceNode, targetNode)
        final resolution = new TransformResolution(graph, path)
        final mapping = new ResolvedMapping([], 'src', null, 'name', resolution, null, [:])
        return resolvedModel(minimalMethod(), [mapping])
    }

    private ResolvedModel resolvedModelWithUnresolvedMapping() {
        final sourceNode = new TypeNode(Stub(TypeMirror), 'Source')
        final targetNode = new TypeNode(Stub(TypeMirror), 'Target')
        final graph = new DefaultDirectedGraph<TypeNode, TransformEdge>(TransformEdge)
        graph.addVertex(sourceNode)
        graph.addVertex(targetNode)
        final edge = new TransformEdge(new DirectAssignableStrategy(), Stub(TransformProposal))
        graph.addEdge(sourceNode, targetNode, edge)
        final resolution = new TransformResolution(graph, null)
        final mapping = new ResolvedMapping([], 'src', null, 'name', resolution, null, [:])
        return resolvedModel(minimalMethod(), [mapping])
    }

    private ResolvedModel resolvedModelWithSharedNodes() {
        // Two mappings whose exploration graphs both contain a 'String' node
        final stringNode1 = new TypeNode(Stub(TypeMirror), 'String')
        final intNode = new TypeNode(Stub(TypeMirror), 'Integer')
        final graph1 = new DefaultDirectedGraph<TypeNode, TransformEdge>(TransformEdge)
        graph1.addVertex(stringNode1)
        graph1.addVertex(intNode)

        final stringNode2 = new TypeNode(Stub(TypeMirror), 'String')
        final longNode = new TypeNode(Stub(TypeMirror), 'Long')
        final graph2 = new DefaultDirectedGraph<TypeNode, TransformEdge>(TransformEdge)
        graph2.addVertex(stringNode2)
        graph2.addVertex(longNode)

        final mapping1 = new ResolvedMapping([], 'src1', null, 'a', new TransformResolution(graph1, null), null, [:])
        final mapping2 = new ResolvedMapping([], 'src2', null, 'b', new TransformResolution(graph2, null), null, [:])
        return resolvedModel(minimalMethod(), [mapping1, mapping2])
    }

    private ResolvedModel resolvedModel(final MappingMethodModel method, final List<ResolvedMapping> mappings) {
        final packageEl = Stub(PackageElement) { getQualifiedName() >> Stub(Name) { toString() >> 'com.example' } }
        final mapperType = Stub(TypeElement) {
            getEnclosingElement() >> packageEl
            getSimpleName() >> Stub(Name) { toString() >> 'TestMapper' }
        }
        return new ResolvedModel(mapperType, [method], [(method): mappings], [:], [:])
    }

    private MappingMethodModel minimalMethod() {
        final exec = Stub(ExecutableElement) { getSimpleName() >> Stub(Name) { toString() >> 'map' } }
        return new MappingMethodModel(exec, Stub(TypeMirror), Stub(TypeMirror), [])
    }
}
