package io.github.joke.percolate.processor

import io.github.joke.percolate.processor.graph.DotRenderer
import io.github.joke.percolate.processor.graph.MapperGraph
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.Filer
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.tools.StandardLocation

@Tag('unit')
class DumpGraphSpec extends Specification {

    Name name(String value) {
        def n = Mock(Name)
        n.toString() >> value
        n
    }

    def 'option off does not write a file'() {
        given:
        def filer = Mock(Filer)
        def diagnostics = Mock(Diagnostics)
        def options = new ProcessorOptions(false)
        def renderer = new DotRenderer()
        def dumpGraph = new DumpGraph(filer, diagnostics, options, renderer)
        def graph = new MapperGraph()
        def typeElement = Mock(TypeElement)

        when:
        dumpGraph.apply(graph, typeElement)

        then:
        0 * filer.createResource(_, _, _, _)
        0 * diagnostics.warning(_, _)
    }

    def 'option on writes a .seed.dot file for non-empty graph'() {
        given:
        def filer = Mock(Filer)
        def diagnostics = Mock(Diagnostics)
        def options = new ProcessorOptions(true)
        def renderer = new DotRenderer()
        def dumpGraph = new DumpGraph(filer, diagnostics, options, renderer)
        def graph = new MapperGraph()
        def scope = Mock(io.github.joke.percolate.processor.graph.Scope)
        scope.encode() >> 'map()'
        def loc = Mock(io.github.joke.percolate.processor.graph.Location)
        loc.encode() >> 'src[x]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'
        def node = new io.github.joke.percolate.processor.graph.Node(Optional.of(typeMirror), loc, scope)
        graph.addNode(node)
        def typeElement = Mock(TypeElement)
        typeElement.getQualifiedName() >> name('com.example.TestMapper')

        and:
        def fileObject = Mock(javax.tools.FileObject)
        1 * filer.createResource(StandardLocation.SOURCE_OUTPUT, '', 'com.example.TestMapper.seed.dot', typeElement) >> fileObject
        def os = Mock(java.io.OutputStream)
        1 * fileObject.openOutputStream() >> os
        os.write(_ as byte[]) >> 0

        when:
        dumpGraph.apply(graph, typeElement)

        then:
        true
    }

    def 'empty graph does not write a file even when option is on'() {
        given:
        def filer = Mock(Filer)
        def diagnostics = Mock(Diagnostics)
        def options = new ProcessorOptions(true)
        def renderer = new DotRenderer()
        def dumpGraph = new DumpGraph(filer, diagnostics, options, renderer)
        def graph = new MapperGraph()
        def typeElement = Mock(TypeElement)

        when:
        dumpGraph.apply(graph, typeElement)

        then:
        0 * filer.createResource(_, _, _, _)
    }

    def 'filer failure is reported as a warning'() {
        given:
        def filer = Mock(Filer)
        def diagnostics = Mock(Diagnostics)
        def options = new ProcessorOptions(true)
        def renderer = new DotRenderer()
        def dumpGraph = new DumpGraph(filer, diagnostics, options, renderer)
        def graph = new MapperGraph()
        def scope = Mock(io.github.joke.percolate.processor.graph.Scope)
        scope.encode() >> 'map()'
        def loc = Mock(io.github.joke.percolate.processor.graph.Location)
        loc.encode() >> 'src[x]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'
        def node = new io.github.joke.percolate.processor.graph.Node(Optional.of(typeMirror), loc, scope)
        graph.addNode(node)
        def typeElement = Mock(TypeElement)
        typeElement.getQualifiedName() >> name('com.example.FailingMapper')

        and:
        def exception = new java.io.IOException('disk full')
        1 * filer.createResource(StandardLocation.SOURCE_OUTPUT, '', 'com.example.FailingMapper.seed.dot', typeElement) >> { throw exception }

        when:
        dumpGraph.apply(graph, typeElement)

        then:
        1 * diagnostics.warning(typeElement, _)
    }

    def 'file name uses .seed.dot infix'() {
        given:
        def filer = Mock(Filer)
        def diagnostics = Mock(Diagnostics)
        def options = new ProcessorOptions(true)
        def renderer = new DotRenderer()
        def dumpGraph = new DumpGraph(filer, diagnostics, options, renderer)
        def graph = new MapperGraph()
        def scope = Mock(io.github.joke.percolate.processor.graph.Scope)
        scope.encode() >> 'map()'
        def loc = Mock(io.github.joke.percolate.processor.graph.Location)
        loc.encode() >> 'src[x]'
        def typeMirror = Mock(javax.lang.model.type.TypeMirror)
        typeMirror.toString() >> 'String'
        def node = new io.github.joke.percolate.processor.graph.Node(Optional.of(typeMirror), loc, scope)
        graph.addNode(node)
        def typeElement = Mock(TypeElement)
        typeElement.getQualifiedName() >> name('com.example.MyMapper')

        and:
        def fileObject = Mock(javax.tools.FileObject)
        1 * filer.createResource(StandardLocation.SOURCE_OUTPUT, _, 'com.example.MyMapper.seed.dot', typeElement) >> fileObject
        def os = Mock(java.io.OutputStream)
        1 * fileObject.openOutputStream() >> os
        os.write(_ as byte[]) >> 0

        when:
        dumpGraph.apply(graph, typeElement)

        then:
        true
    }
}
