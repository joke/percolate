package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.ProcessorOptions
import io.github.joke.percolate.processor.graph.AccessEdge
import io.github.joke.percolate.processor.graph.MappingEdge
import io.github.joke.percolate.processor.graph.MappingGraph
import io.github.joke.percolate.processor.graph.SourcePropertyNode
import io.github.joke.percolate.processor.graph.SourceRootNode
import io.github.joke.percolate.processor.graph.TargetPropertyNode
import io.github.joke.percolate.processor.graph.TransformEdge
import io.github.joke.percolate.processor.graph.TypeNode
import io.github.joke.percolate.processor.model.MappingMethodModel
import io.github.joke.percolate.processor.spi.DirectAssignableStrategy
import io.github.joke.percolate.processor.transform.ResolvedMapping
import io.github.joke.percolate.processor.transform.ResolvedModel
import io.github.joke.percolate.processor.transform.TransformProposal
import io.github.joke.percolate.processor.transform.TransformResolution
import org.jgrapht.GraphPath
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
class DumpResolvedOverlayStageSpec extends Specification {

    Filer filer = Mock()
    Messager messager = Mock()
    DumpResolvedOverlayStage stage = new DumpResolvedOverlayStage(new ProcessorOptions(true, 'dot'), filer, messager)

    def 'is a no-op when debug graphs are disabled'() {
        given:
        final stage = new DumpResolvedOverlayStage(new ProcessorOptions(false, 'dot'), filer, messager)
        final pair = minimalPair()

        when:
        stage.execute(pair[0] as MappingGraph, pair[1] as ResolvedModel)

        then:
        0 * filer._
    }

    def 'annotates mapping edge as unresolved when no transform resolution'() {
        given:
        final writer = new StringWriter()
        filer.createResource(_, _, _) >> Stub(FileObject) { openWriter() >> writer }
        final mapping = mappingWithResolution(null)
        final pair = pairFor(mapping)

        when:
        stage.execute(pair[0] as MappingGraph, pair[1] as ResolvedModel)

        then:
        writer.toString().contains('map: unresolved')
    }

    def 'annotates mapping edge as direct when path has no transform edges'() {
        given:
        final writer = new StringWriter()
        filer.createResource(_, _, _) >> Stub(FileObject) { openWriter() >> writer }
        final emptyPath = Stub(GraphPath) { getEdgeList() >> [] }
        final resolution = new TransformResolution(new DefaultDirectedGraph<>(TransformEdge), emptyPath)
        final mapping = mappingWithResolution(resolution)
        final pair = pairFor(mapping)

        when:
        stage.execute(pair[0] as MappingGraph, pair[1] as ResolvedModel)

        then:
        writer.toString().contains('map: direct')
    }

    def 'annotates mapping edge with type chain when path has transform edges'() {
        given:
        final writer = new StringWriter()
        filer.createResource(_, _, _) >> Stub(FileObject) { openWriter() >> writer }
        final sourceNode = new TypeNode(Stub(TypeMirror), 'Source')
        final targetNode = new TypeNode(Stub(TypeMirror), 'Target')
        final graph = new DefaultDirectedGraph<TypeNode, TransformEdge>(TransformEdge)
        graph.addVertex(sourceNode)
        graph.addVertex(targetNode)
        final edge = new TransformEdge(new DirectAssignableStrategy(), Stub(TransformProposal))
        edge.resolveTemplate({ input -> input })
        graph.addEdge(sourceNode, targetNode, edge)
        final path = new BFSShortestPath<>(graph).getPath(sourceNode, targetNode)
        final resolution = new TransformResolution(graph, path)
        final mapping = mappingWithResolution(resolution)
        final pair = pairFor(mapping)

        when:
        stage.execute(pair[0] as MappingGraph, pair[1] as ResolvedModel)

        then:
        writer.toString().contains('Source')
        writer.toString().contains('Target')
        writer.toString().contains('DirectAssignableStrategy')
    }

    def 'catches IOException from filer and logs warning without aborting'() {
        given:
        filer.createResource(_, _, _) >> { throw new IOException('disk full') }
        final pair = minimalPair()

        when:
        stage.execute(pair[0] as MappingGraph, pair[1] as ResolvedModel)

        then:
        1 * messager.printMessage(WARNING, { it.contains('TestMapper_map_resolved') })
    }

    private ResolvedMapping mappingWithResolution(final TransformResolution resolution) {
        return new ResolvedMapping([], 'src', null, 'name', resolution, null)
    }

    private List pairFor(final ResolvedMapping mapping) {
        final method = minimalMethod()
        return [mappingGraph(method), resolvedModel(method, [mapping])]
    }

    private List minimalPair() {
        return pairFor(mappingWithResolution(null))
    }

    private MappingGraph mappingGraph(final MappingMethodModel method) {
        final graph = new DefaultDirectedGraph<Object, Object>(Object)
        final src = new SourceRootNode('source')
        final prop = new SourcePropertyNode('name')
        final tgt = new TargetPropertyNode('name')
        graph.addVertex(src)
        graph.addVertex(prop)
        graph.addVertex(tgt)
        graph.addEdge(src, prop, new AccessEdge())
        graph.addEdge(prop, tgt, new MappingEdge())

        final packageEl = Stub(PackageElement) { getQualifiedName() >> Stub(Name) { toString() >> 'com.example' } }
        final mapperType = Stub(TypeElement) {
            getEnclosingElement() >> packageEl
            getSimpleName() >> Stub(Name) { toString() >> 'TestMapper' }
        }
        return new MappingGraph(mapperType, [method], [(method): graph])
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
