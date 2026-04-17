package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.ProcessorOptions
import io.github.joke.percolate.processor.graph.PropertyNode
import io.github.joke.percolate.processor.graph.PropertyReadEdge
import io.github.joke.percolate.processor.graph.SourceParamNode
import io.github.joke.percolate.processor.graph.TargetSlotNode
import io.github.joke.percolate.processor.graph.ValueEdge
import io.github.joke.percolate.processor.graph.ValueGraphResult
import io.github.joke.percolate.processor.graph.ValueNode
import io.github.joke.percolate.processor.match.MethodMatching
import io.github.joke.percolate.processor.model.MappingMethodModel
import io.github.joke.percolate.processor.model.ReadAccessor
import io.github.joke.percolate.processor.model.WriteAccessor
import org.jgrapht.graph.DefaultDirectedGraph
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import javax.tools.FileObject

import static javax.tools.Diagnostic.Kind.WARNING

@Tag('unit')
class DumpValueGraphStageSpec extends Specification {

    Filer filer = Mock()
    Messager messager = Mock()
    DumpValueGraphStage stage = new DumpValueGraphStage(new ProcessorOptions(true, 'dot'), filer, messager)

    def 'is a no-op when debug graphs are disabled'() {
        given:
        final stage = new DumpValueGraphStage(new ProcessorOptions(false, 'dot'), filer, messager)

        when:
        stage.execute(mapperType(), minimalResult())

        then:
        0 * filer._
    }

    def 'produces DOT output containing node labels for param, property, and slot'() {
        given:
        final writer = new StringWriter()
        filer.createResource(_, _, _) >> Stub(FileObject) { openWriter() >> writer }

        when:
        stage.execute(mapperType(), minimalResult())

        then:
        writer.toString().contains('label="param:test.Order"')
        writer.toString().contains('label="prop:name"')
        writer.toString().contains('label="slot:name"')
        writer.toString().contains('label="read"')
    }

    def 'output file name is based on mapper and method name'() {
        given:
        String capturedName = null
        filer.createResource(_, _, _) >> { args ->
            capturedName = args[2]
            Stub(FileObject) { openWriter() >> new StringWriter() }
        }

        when:
        stage.execute(mapperType(), minimalResult())

        then:
        capturedName == 'TestMapper_map_valuegraph.dot'
    }

    def 'output file extension matches the configured format'() {
        given:
        final stage = new DumpValueGraphStage(new ProcessorOptions(true, format), filer, messager)
        String capturedName = null
        filer.createResource(_, _, _) >> { args ->
            capturedName = args[2]
            Stub(FileObject) { openWriter() >> new StringWriter() }
        }

        when:
        stage.execute(mapperType(), minimalResult())

        then:
        capturedName == "TestMapper_map_valuegraph.${expectedExt}"

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
        stage.execute(mapperType(), minimalResult())

        then:
        1 * messager.printMessage(WARNING, { it.contains('TestMapper_map_valuegraph') })
    }

    def 'skips methods whose graph has no vertices'() {
        given:
        final emptyGraph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final result = new ValueGraphResult([(matching()): emptyGraph], [:])

        when:
        stage.execute(mapperType(), result)

        then:
        0 * filer._
    }

    private ValueGraphResult minimalResult() {
        final orderType  = typeMirror('test.Order')
        final stringType = typeMirror('java.lang.String')

        final graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final paramEl = Stub(VariableElement) { getSimpleName() >> Stub(Name) { toString() >> 'order' } }
        final paramNode = new SourceParamNode(paramEl, orderType)
        final propNode = new PropertyNode('name', stringType, Stub(ReadAccessor))
        final slotNode = new TargetSlotNode('name', stringType, Stub(WriteAccessor))
        graph.addVertex(paramNode)
        graph.addVertex(propNode)
        graph.addVertex(slotNode)
        graph.addEdge(paramNode, propNode, new PropertyReadEdge())
        graph.addEdge(propNode, slotNode, new PropertyReadEdge())

        return new ValueGraphResult([(matching()): graph], [:])
    }

    private MethodMatching matching() {
        final exec = Stub(ExecutableElement) { getSimpleName() >> Stub(Name) { toString() >> 'map' } }
        final model = Stub(MappingMethodModel) { getMethod() >> exec }
        return new MethodMatching(exec, model, [])
    }

    private TypeElement mapperType() {
        final packageEl = Stub(PackageElement) { getQualifiedName() >> Stub(Name) { toString() >> 'com.example' } }
        Stub(TypeElement) {
            getEnclosingElement() >> packageEl
            getSimpleName() >> Stub(Name) { toString() >> 'TestMapper' }
        }
    }

    private TypeMirror typeMirror(final String name) {
        Stub(TypeMirror) { toString() >> name }
    }
}
