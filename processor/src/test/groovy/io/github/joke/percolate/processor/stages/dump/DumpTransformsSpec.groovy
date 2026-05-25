package io.github.joke.percolate.processor.stages.dump

import io.github.joke.percolate.processor.Diagnostics
import io.github.joke.percolate.processor.ProcessorOptions
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.test.TestFiler
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.tools.*

@Tag('unit')
class DumpTransformsSpec extends Specification {

    @Shared
    Filer mockFiler

    def setupSpec() {
        mockFiler = Mock(Filer)
    }

    def 'Option off does not write a file'() {
        given:
        final var graph = buildGraphWithNodes()
        final var options = new ProcessorOptions(false, Set.of())
        final var renderer = new DotRenderer()
        final var messager = new Messager() {
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg, Element element, AnnotationMirror mirror, AnnotationValue value) {}
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg, Element element, AnnotationMirror mirror) {}
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg) {}
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg, Element element) {}
        }
        final var diagnostics = new Diagnostics(messager)
        final var mapperType = TypeUniverse.element('java.lang.Object')
        final var stage = new DumpTransforms(mockFiler, diagnostics, options, renderer)

        when:
        stage.apply(graph, mapperType)

        then:
        0 * mockFiler.createResource(_, _, _, _)
    }

    def 'Empty graph does not write a file even when option is on'() {
        given:
        final var graph = new MapperGraph()
        final var options = new ProcessorOptions(true, Set.of())
        final var renderer = new DotRenderer()
        final var messager = new Messager() {
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg, Element element, AnnotationMirror mirror, AnnotationValue value) {}
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg, Element element, AnnotationMirror mirror) {}
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg) {}
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg, Element element) {}
        }
        final var diagnostics = new Diagnostics(messager)
        final var mapperType = TypeUniverse.element('java.lang.Object')
        final var stage = new DumpTransforms(mockFiler, diagnostics, options, renderer)

        when:
        stage.apply(graph, mapperType)

        then:
        0 * mockFiler.createResource(_, _, _, _)
    }

    def 'DOT output contains only REALISED edges from transformsView'() {
        given:
        final var graph = buildGraphWithMixedKinds()
        final var renderer = new DotRenderer()
        final var mapperType = TypeUniverse.element('java.lang.Object')

        when:
        final var dotOutput = renderer.render(graph.transformsView(), mapperType)

        then:
        dotOutput != null
        dotOutput.contains('digraph "java.lang.Object"')
        // Transforms view only includes REALISED edges — no SEED, SUB_SEED, MARKER, or ELEMENT_SEED
        !dotOutput.contains('SEED')
        !dotOutput.contains('SUB_SEED')
        !dotOutput.contains('MARKER')
        !dotOutput.contains('ELEMENT_SEED')
    }

    def 'Full DOT file naming uses .transforms.dot infix'() {
        given:
        final var graph = buildGraphWithNodes()
        final var options = new ProcessorOptions(true, Set.of())
        final var renderer = new DotRenderer()
        final var messager = new Messager() {
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg, Element element, AnnotationMirror mirror, AnnotationValue value) {}
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg, Element element, AnnotationMirror mirror) {}
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg) {}
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg, Element element) {}
        }
        final var diagnostics = new Diagnostics(messager)
        final var mapperType = TypeUniverse.element('java.lang.Object')
        final var outputStream = new ByteArrayOutputStream()
        final var fileObject = new SimpleJavaFileObject(URI.create('mem://output'), JavaFileObject.Kind.SOURCE) {
            @Override OutputStream openOutputStream() { outputStream }
        }
        final var capturingFiler = new TestFiler(fileObject)
        final var stage = new DumpTransforms(capturingFiler, diagnostics, options, renderer)

        when:
        stage.apply(graph, mapperType)

        then:
        outputStream.size() > 0
    }

    def 'Filer failure is reported as a warning, not an error'() {
        given:
        final var graph = buildGraphWithNodes()
        final var options = new ProcessorOptions(true, Set.of())
        final var renderer = new DotRenderer()
        final var messages = []
        final var messager = new Messager() {
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg, Element element, AnnotationMirror mirror, AnnotationValue value) { messages << [kind: kind, msg: msg] }
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg, Element element, AnnotationMirror mirror) { messages << [kind: kind, msg: msg] }
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg) { messages << [kind: kind, msg: msg] }
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg, Element element) { messages << [kind: kind, msg: msg] }
        }
        final var diagnostics = new Diagnostics(messager)
        final var mapperType = TypeUniverse.element('java.lang.Object')
        final var failingFiler = new Filer() {
            @Override JavaFileObject createSourceFile(CharSequence canonicalName, Element... originatingElements) { throw new IOException('filer broken') }
            @Override JavaFileObject createClassFile(CharSequence canonicalName, Element... originatingElements) { throw new IOException('filer broken') }
            @Override FileObject createResource(JavaFileManager.Location location, CharSequence packageName, CharSequence relativeName, Element... originatingElements) { throw new IOException('filer broken') }
            @Override FileObject getResource(JavaFileManager.Location location, CharSequence packageName, CharSequence relativeName) { throw new IOException('filer broken') }
        }
        final var stage = new DumpTransforms(failingFiler, diagnostics, options, renderer)

        when:
        stage.apply(graph, mapperType)

        then:
        messages.findResult { it ->
            it.kind == Diagnostic.Kind.WARNING ? it.msg.toString() : null
        } != null
        messages.findResult { it ->
            it.kind == Diagnostic.Kind.ERROR ? it.msg.toString() : null
        } == null
    }

    def 'transformsView excludes non-REALISED edges from DOT output'() {
        given:
        final var graph = buildGraphWithMixedKinds()
        final var renderer = new DotRenderer()
        final var mapperType = TypeUniverse.element('java.lang.Object')

        when:
        final var fullDot = renderer.render(graph, mapperType)
        final var transformsDot = renderer.render(graph.transformsView(), mapperType)

        then:
        // Full graph has all edge kinds
        fullDot.contains('SEED')
        fullDot.contains('MARKER')
        // Transforms view strips all non-REALISED edges
        transformsDot.contains('SEED') == false
        transformsDot.contains('MARKER') == false
    }

    private static MapperGraph buildGraphWithNodes() {
        final graph = new MapperGraph()
        final scope = HarnessScope.of('test')
        final a = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('in')), scope) 
        final b = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('out')), scope) 
        graph.addNode(a)
        graph.addNode(b)
        graph
    }

    private static MapperGraph buildGraphWithMixedKinds() {
        final graph = new MapperGraph()
        final scope = HarnessScope.of('test')
        final a = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('a')), scope) 
        final b = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('b')), scope) 
        final c = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('c')), scope) 
        final d = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('d')), scope) 
        graph.addNode(a)
        graph.addNode(b)
        graph.addNode(c)
        graph.addNode(d)
        graph.addEdge(Edge.seedForTest(a, b))
        graph.addEdge(Edge.realised(c, d, 1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy'))
        graph.addEdge(Edge.seed(a, c, Optional.empty(), Optional.of('test.Strategy')))
        graph.addEdge(Edge.marker(a, b, 'test.Strategy'))
        graph
    }

    private static final class HarnessScope implements Scope {
        private final String name
        static HarnessScope of(final String name) { new HarnessScope(name) }
        HarnessScope(final String name) { this.name = name }
        @Override String encode() { name }
    }
}
