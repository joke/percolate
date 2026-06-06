package io.github.joke.percolate.processor.stages.dump

import io.github.joke.percolate.processor.Diagnostics
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.ProcessorOptions
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.tools.*

@Tag('unit')
class GraphDumpWriterSpec extends Specification {

    def 'option off writes no file'() {
        given:
        final graph = new MapperGraph()
        graph.addNode(node(new HarnessScope('m()'), new SourceLocation(AccessPath.of('a'))))
        final filer = new RecordingFiler()
        final writer = new GraphDumpWriter(filer, silentDiagnostics(), new ProcessorOptions(false, Set.of()), new DotRenderer())

        when:
        writer.dump(ctxFor(graph), 'seed') { it }

        then:
        filer.writes == [:]
    }

    def 'empty graph writes no file even when option on'() {
        given:
        final filer = new RecordingFiler()
        final writer = new GraphDumpWriter(filer, silentDiagnostics(), new ProcessorOptions(true, Set.of()), new DotRenderer())

        when:
        writer.dump(ctxFor(new MapperGraph()), 'seed') { it }

        then:
        filer.writes == [:]
    }

    def 'writes one file per scope, named <fqn>.<method>.<view>.dot'() {
        given:
        final scopeA = new HarnessScope('mapHuman')
        final scopeB = new HarnessScope('mapAddress')
        final graph = new MapperGraph()
        graph.addNode(node(scopeA, new SourceLocation(AccessPath.of('a'))))
        graph.addNode(node(scopeA, new TargetLocation(TargetPath.of('b'))))
        graph.addNode(node(scopeB, new SourceLocation(AccessPath.of('c'))))
        final filer = new RecordingFiler()
        final writer = new GraphDumpWriter(filer, silentDiagnostics(), new ProcessorOptions(true, Set.of()), new DotRenderer())

        when:
        writer.dump(ctxFor(graph), 'seed') { it }

        then:
        filer.writes.keySet() == [
                'java.lang.Object.mapHuman.seed.dot',
                'java.lang.Object.mapAddress.seed.dot',
        ] as Set
    }

    def 'overloaded method scopes are disambiguated with a -<n> index'() {
        given:
        // two identity-distinct scopes that share a sanitised infix base
        final scope0 = new HarnessScope('map()')
        final scope1 = new HarnessScope('map()')
        final graph = new MapperGraph()
        graph.addNode(node(scope0, new SourceLocation(AccessPath.of('a'))))
        graph.addNode(node(scope1, new SourceLocation(AccessPath.of('b'))))
        final filer = new RecordingFiler()
        final writer = new GraphDumpWriter(filer, silentDiagnostics(), new ProcessorOptions(true, Set.of()), new DotRenderer())

        when:
        writer.dump(ctxFor(graph), 'seed') { it }

        then:
        filer.writes.keySet() == [
                'java.lang.Object.map-0.seed.dot',
                'java.lang.Object.map-1.seed.dot',
        ] as Set
    }

    def 'a cross-scope edge is rendered in its from-node scope file only'() {
        given:
        final scopeA = new HarnessScope('a')
        final scopeB = new HarnessScope('b')
        final from = node(scopeA, new SourceLocation(AccessPath.of('x')))
        final to = node(scopeB, new TargetLocation(TargetPath.of('y')))
        final graph = new MapperGraph()
        graph.addNode(from)
        graph.addNode(to)
        graph.addEdge(from, to, Edge.realised(1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy'))
        final filer = new RecordingFiler()
        final writer = new GraphDumpWriter(filer, silentDiagnostics(), new ProcessorOptions(true, Set.of()), new DotRenderer())

        when:
        writer.dump(ctxFor(graph), 'full') { it }
        final contents = filer.contents()

        then:
        contents['java.lang.Object.a.full.dot'].contains('->')
        !contents['java.lang.Object.b.full.dot'].contains('->')
    }

    def 'filer failure is reported as a warning, not an error'() {
        given:
        final graph = new MapperGraph()
        graph.addNode(node(new HarnessScope('m'), new SourceLocation(AccessPath.of('a'))))
        final messages = []
        final failingFiler = new Filer() {
            @Override JavaFileObject createSourceFile(CharSequence n, Element... e) { throw new IOException('broken') }
            @Override JavaFileObject createClassFile(CharSequence n, Element... e) { throw new IOException('broken') }
            @Override FileObject createResource(JavaFileManager.Location l, CharSequence p, CharSequence r, Element... e) { throw new IOException('broken') }
            @Override FileObject getResource(JavaFileManager.Location l, CharSequence p, CharSequence r) { throw new IOException('broken') }
        }
        final writer = new GraphDumpWriter(failingFiler, silentDiagnostics(messages), new ProcessorOptions(true, Set.of()), new DotRenderer())

        when:
        writer.dump(ctxFor(graph), 'seed') { it }

        then:
        messages.any { it.kind == Diagnostic.Kind.WARNING }
        !messages.any { it.kind == Diagnostic.Kind.ERROR }
    }

    private static Diagnostics silentDiagnostics(List sink = null) {
        new Diagnostics(new Messager() {
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e, AnnotationMirror m, AnnotationValue v) { sink?.add([kind: kind, msg: msg]) }
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e, AnnotationMirror m) { sink?.add([kind: kind, msg: msg]) }
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg) { sink?.add([kind: kind, msg: msg]) }
            @Override void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e) { sink?.add([kind: kind, msg: msg]) }
        })
    }

    private static Node node(Scope scope, Location loc) {
        new Node(Optional.of(TypeUniverse.STRING), loc, scope)
    }

    private static MapperContext ctxFor(MapperGraph graph) {
        final ctx = new MapperContext(TypeUniverse.element('java.lang.Object'))
        ctx.graph = graph
        ctx
    }

    /** Filer that records each written resource's relativeName -> UTF-8 content. */
    private static final class RecordingFiler implements Filer {
        final Map<String, ByteArrayOutputStream> writes = [:]
        @Override JavaFileObject createSourceFile(CharSequence n, Element... e) { throw new UnsupportedOperationException() }
        @Override JavaFileObject createClassFile(CharSequence n, Element... e) { throw new UnsupportedOperationException() }
        @Override FileObject getResource(JavaFileManager.Location l, CharSequence p, CharSequence r) { throw new UnsupportedOperationException() }
        @Override FileObject createResource(JavaFileManager.Location l, CharSequence p, CharSequence relativeName, Element... e) {
            final baos = new ByteArrayOutputStream()
            writes[relativeName.toString()] = baos
            new SimpleJavaFileObject(URI.create('mem://' + relativeName), JavaFileObject.Kind.SOURCE) {
                @Override OutputStream openOutputStream() { baos }
            }
        }
        Map<String, String> contents() { writes.collectEntries { k, v -> [k, v.toString('UTF-8')] } }
    }
}
