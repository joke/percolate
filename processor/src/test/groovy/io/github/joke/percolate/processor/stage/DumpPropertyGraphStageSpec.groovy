package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.ProcessorOptions
import io.github.joke.percolate.processor.graph.AccessEdge
import io.github.joke.percolate.processor.graph.MappingEdge
import io.github.joke.percolate.processor.graph.MappingGraph
import io.github.joke.percolate.processor.graph.SourcePropertyNode
import io.github.joke.percolate.processor.graph.SourceRootNode
import io.github.joke.percolate.processor.graph.TargetPropertyNode
import io.github.joke.percolate.processor.model.MappingMethodModel
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
class DumpPropertyGraphStageSpec extends Specification {

    Filer filer = Mock()
    Messager messager = Mock()
    DumpPropertyGraphStage stage = new DumpPropertyGraphStage(new ProcessorOptions(true, 'dot'), filer, messager)

    def 'is a no-op when debug graphs are disabled'() {
        given:
        final stage = new DumpPropertyGraphStage(new ProcessorOptions(false, 'dot'), filer, messager)

        when:
        stage.execute(minimalMappingGraph())

        then:
        0 * filer._
    }

    def 'produces DOT output containing edge labels for access and mapping edges'() {
        given:
        final writer = new StringWriter()
        filer.createResource(_, _, _) >> Stub(FileObject) { openWriter() >> writer }

        when:
        stage.execute(minimalMappingGraph())

        then:
        writer.toString().contains('label="access"')
        writer.toString().contains('label="mapping"')
    }

    def 'output file name is based on mapper and method name'() {
        given:
        String capturedName = null
        filer.createResource(_, _, _) >> { args ->
            capturedName = args[2]
            Stub(FileObject) { openWriter() >> new StringWriter() }
        }

        when:
        stage.execute(minimalMappingGraph())

        then:
        capturedName == 'TestMapper_map_property.dot'
    }

    def 'output file extension matches the configured format'() {
        given:
        final stage = new DumpPropertyGraphStage(new ProcessorOptions(true, format), filer, messager)
        String capturedName = null
        filer.createResource(_, _, _) >> { args ->
            capturedName = args[2]
            Stub(FileObject) { openWriter() >> new StringWriter() }
        }

        when:
        stage.execute(minimalMappingGraph())

        then:
        capturedName == "TestMapper_map_property.${expectedExt}"

        where:
        format      || expectedExt
        'dot'       || 'dot'
        'graphml'   || 'graphml'
        'json'      || 'json'
        'unknown'   || 'dot'
    }

    def 'catches IOException from filer and logs warning without aborting'() {
        given:
        filer.createResource(_, _, _) >> { throw new IOException('disk full') }

        when:
        stage.execute(minimalMappingGraph())

        then:
        1 * messager.printMessage(WARNING, { it.contains('TestMapper_map_property') })
    }

    private MappingGraph minimalMappingGraph() {
        final graph = new DefaultDirectedGraph<Object, Object>(Object)
        final src = new SourceRootNode('source')
        final prop = new SourcePropertyNode('name')
        final tgt = new TargetPropertyNode('name')
        graph.addVertex(src)
        graph.addVertex(prop)
        graph.addVertex(tgt)
        graph.addEdge(src, prop, new AccessEdge())
        graph.addEdge(prop, tgt, new MappingEdge())

        final exec = Stub(ExecutableElement) { getSimpleName() >> Stub(Name) { toString() >> 'map' } }
        final method = new MappingMethodModel(exec, Stub(TypeMirror), Stub(TypeMirror), [])

        final packageEl = Stub(PackageElement) { getQualifiedName() >> Stub(Name) { toString() >> 'com.example' } }
        final mapperType = Stub(TypeElement) {
            getEnclosingElement() >> packageEl
            getSimpleName() >> Stub(Name) { toString() >> 'TestMapper' }
        }

        return new MappingGraph(mapperType, [method], [(method): graph])
    }
}
