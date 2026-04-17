package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.ProcessorOptions
import io.github.joke.percolate.processor.graph.PropertyNode
import io.github.joke.percolate.processor.graph.PropertyReadEdge
import io.github.joke.percolate.processor.graph.SourceParamNode
import io.github.joke.percolate.processor.graph.TargetSlotNode
import io.github.joke.percolate.processor.graph.ValueEdge
import io.github.joke.percolate.processor.graph.ValueGraphResult
import io.github.joke.percolate.processor.graph.ValueNode
import io.github.joke.percolate.processor.match.AssignmentOrigin
import io.github.joke.percolate.processor.match.MappingAssignment
import io.github.joke.percolate.processor.match.MethodMatching
import io.github.joke.percolate.processor.match.ResolvedAssignment
import io.github.joke.percolate.processor.model.MappingMethodModel
import io.github.joke.percolate.processor.model.ReadAccessor
import io.github.joke.percolate.processor.model.WriteAccessor
import org.jgrapht.GraphPath
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
class DumpResolvedPathsStageSpec extends Specification {

    Filer filer = Mock()
    Messager messager = Mock()
    DumpResolvedPathsStage stage = new DumpResolvedPathsStage(new ProcessorOptions(true, 'dot'), filer, messager)

    def 'is a no-op when debug graphs are disabled'() {
        given:
        final stage = new DumpResolvedPathsStage(new ProcessorOptions(false, 'dot'), filer, messager)

        when:
        stage.execute(mapperType(), minimalResult(), [:])

        then:
        0 * filer._
    }

    def 'produces DOT output with bold style on winning edges'() {
        given:
        final data = graphWithWinningPath()
        final writer = new StringWriter()
        filer.createResource(_, _, _) >> Stub(FileObject) { openWriter() >> writer }

        when:
        stage.execute(mapperType(), data.result, data.resolved)

        then:
        writer.toString().contains('style="bold"')
        writer.toString().contains('label="read"')
    }

    def 'non-dot formats flag winning edges via winning attribute'() {
        given:
        final data = graphWithWinningPath()
        final stage = new DumpResolvedPathsStage(new ProcessorOptions(true, 'graphml'), filer, messager)
        final writer = new StringWriter()
        filer.createResource(_, _, _) >> Stub(FileObject) { openWriter() >> writer }

        when:
        stage.execute(mapperType(), data.result, data.resolved)

        then:
        writer.toString().contains('winning')
    }

    def 'output file name uses resolved suffix'() {
        given:
        String capturedName = null
        filer.createResource(_, _, _) >> { args ->
            capturedName = args[2]
            Stub(FileObject) { openWriter() >> new StringWriter() }
        }

        when:
        stage.execute(mapperType(), minimalResult(), [:])

        then:
        capturedName == 'TestMapper_map_resolved.dot'
    }

    def 'catches IOException from filer and logs warning without aborting'() {
        given:
        filer.createResource(_, _, _) >> { throw new IOException('disk full') }

        when:
        stage.execute(mapperType(), minimalResult(), [:])

        then:
        1 * messager.printMessage(WARNING, { it.contains('TestMapper_map_resolved') })
    }

    def 'skips methods whose graph has no vertices'() {
        given:
        final emptyGraph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final result = new ValueGraphResult([(matching()): emptyGraph], [:])

        when:
        stage.execute(mapperType(), result, [:])

        then:
        0 * filer._
    }

    private static class GraphData {
        ValueGraphResult result
        Map<MethodMatching, List<ResolvedAssignment>> resolved
    }

    private GraphData graphWithWinningPath() {
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
        final readEdge = new PropertyReadEdge()
        final writeEdge = new PropertyReadEdge()
        graph.addEdge(paramNode, propNode, readEdge)
        graph.addEdge(propNode, slotNode, writeEdge)

        final path = Stub(GraphPath) { getEdgeList() >> [readEdge, writeEdge] }
        final assignment = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)
        final ra = new ResolvedAssignment(assignment, path, null)
        final m = matching()

        final data = new GraphData()
        data.result = new ValueGraphResult([(m): graph], [:])
        data.resolved = [(m): [ra]]
        return data
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
