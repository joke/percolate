package io.github.joke.percolate.processor.stage

import com.palantir.javapoet.CodeBlock
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
import io.github.joke.percolate.processor.model.WriteAccessor
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
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import javax.tools.FileObject

import static javax.tools.Diagnostic.Kind.WARNING

@Tag('unit')
class DumpGraphStageSpec extends Specification {

    Filer filer = Mock()
    Messager messager = Mock()
    MethodMatching matching = matching()
    DefaultDirectedGraph<ValueNode, ValueEdge> graph = buildGraph()
    ValueGraphResult result = new ValueGraphResult([(matching): graph], [:])
    Map<MethodMatching, List<ResolvedAssignment>> resolvedAssignments = buildResolvedAssignments(matching, graph)

    def 'is a no-op when debug graphs are disabled'() {
        given:
        final stage = new DumpGraphStage(new ProcessorOptions(false, 'dot'), filer, messager)

        when:
        stage.execute(mapperType(), result, resolvedAssignments)

        then:
        0 * filer._
    }

    def 'output file name uses resolved suffix and mapper + method name'() {
        given:
        final stage = new DumpGraphStage(new ProcessorOptions(true, 'dot'), filer, messager)
        String capturedName = null
        filer.createResource(_, _, _) >> { args ->
            capturedName = args[2]
            Stub(FileObject) { openWriter() >> new StringWriter() }
        }

        when:
        stage.execute(mapperType(), result, resolvedAssignments)

        then:
        capturedName == 'TestMapper_map_resolved.dot'
    }

    def 'DOT output bolds edges on the resolved path'() {
        given:
        final stage = new DumpGraphStage(new ProcessorOptions(true, 'dot'), filer, messager)
        final writer = new StringWriter()
        filer.createResource(_, _, _) >> Stub(FileObject) { openWriter() >> writer }

        when:
        stage.execute(mapperType(), result, resolvedAssignments)

        then:
        final dot = writer.toString()
        dot.contains('style="bold"')
    }

    def 'DOT output does not bold any edge when no winning path exists'() {
        given:
        final stage = new DumpGraphStage(new ProcessorOptions(true, 'dot'), filer, messager)
        final writer = new StringWriter()
        filer.createResource(_, _, _) >> Stub(FileObject) { openWriter() >> writer }

        when:
        stage.execute(mapperType(), result, [(matching): []])

        then:
        !writer.toString().contains('style="bold"')
    }

    def 'output file extension matches the configured format'() {
        given:
        final stage = new DumpGraphStage(new ProcessorOptions(true, format), filer, messager)
        String capturedName = null
        filer.createResource(_, _, _) >> { args ->
            capturedName = args[2]
            Stub(FileObject) { openWriter() >> new StringWriter() }
        }

        when:
        stage.execute(mapperType(), result, resolvedAssignments)

        then:
        capturedName == "TestMapper_map_resolved.${expectedExt}"

        where:
        format      || expectedExt
        'dot'       || 'dot'
        'graphml'   || 'graphml'
        'json'      || 'json'
        'unknown'   || 'dot'
    }

    def 'catches IOException from filer and logs warning without aborting'() {
        given:
        final stage = new DumpGraphStage(new ProcessorOptions(true, 'dot'), filer, messager)
        filer.createResource(_, _, _) >> { throw new IOException('disk full') }

        when:
        stage.execute(mapperType(), result, resolvedAssignments)

        then:
        1 * messager.printMessage(WARNING, { it.contains('TestMapper_map_resolved') })
    }

    def 'skips methods whose graph has no vertices'() {
        given:
        final stage = new DumpGraphStage(new ProcessorOptions(true, 'dot'), filer, messager)
        final emptyGraph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final emptyResult = new ValueGraphResult([(matching): emptyGraph], [:])

        when:
        stage.execute(mapperType(), emptyResult, [(matching): []])

        then:
        0 * filer._
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<MethodMatching, List<ResolvedAssignment>> buildResolvedAssignments(
            final MethodMatching matching,
            final DefaultDirectedGraph<ValueNode, ValueEdge> graph) {
        final src  = graph.vertexSet().find { it instanceof SourceParamNode }
        final slot = graph.vertexSet().find { it instanceof TargetSlotNode }
        final path = new BFSShortestPath<>(graph).getPath(src, slot)
        final assignment = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)
        return [(matching): [new ResolvedAssignment(assignment, path, null)]]
    }

    private DefaultDirectedGraph<ValueNode, ValueEdge> buildGraph() {
        final orderType  = typeMirror('test.Order')
        final stringType = typeMirror('java.lang.String')

        final graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final paramEl = Stub(VariableElement) { getSimpleName() >> Stub(Name) { toString() >> 'order' } }
        final paramNode = new SourceParamNode(paramEl, orderType)
        final propNode = new PropertyNode('name', stringType)
        final slotNode = new TargetSlotNode('name', stringType, Stub(WriteAccessor))
        graph.addVertex(paramNode)
        graph.addVertex(propNode)
        graph.addVertex(slotNode)
        graph.addEdge(paramNode, propNode, new PropertyReadEdge({ input -> CodeBlock.of('$L.getName()', input) }))
        graph.addEdge(propNode, slotNode, new PropertyReadEdge({ input -> CodeBlock.of('$L', input) }))
        return graph
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
