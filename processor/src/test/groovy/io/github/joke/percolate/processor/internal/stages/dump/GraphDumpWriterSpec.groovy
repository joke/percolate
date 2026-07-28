package io.github.joke.percolate.processor.internal.stages.dump

import io.github.joke.percolate.processor.Diagnostic
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.ProcessorOptions
import io.github.joke.percolate.processor.internal.graph.AccessPath
import io.github.joke.percolate.processor.internal.graph.AddOperation
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.DotRenderer
import io.github.joke.percolate.processor.internal.graph.GraphVertex
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.PortBinding
import io.github.joke.percolate.processor.internal.graph.Scope
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.internal.graph.TargetPath
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.Subjects
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.Filer
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.tools.FileObject
import javax.tools.StandardLocation

@Tag('unit')
class GraphDumpWriterSpec extends Specification {

    Filer filer = Mock()
    ProcessorOptions options = Mock()
    DotRenderer dotRenderer = Mock()
    GraphDumpWriter dumpWriter = new GraphDumpWriter(filer, options, dotRenderer)

    TypeMirror STRING = Mock()
    MapperGraph graph = new MapperGraph()
    Scope scope = new HarnessScope('m()')
    TypeElement mapperType = Stub()

    def ctx = new MapperContext(mapperType)

    def 'dumpWithRefusals asks the renderer to draw refusals'() {
        GraphDumpWriter writer = Spy(constructorArgs: [filer, options, dotRenderer])
        def include = { true }

        when:
        writer.dumpWithRefusals(ctx, 'plan', include, false)

        then:
        1 * writer.dump(ctx, 'plan', { it.test(Stub(GraphVertex)) }, false, true)
        1 * writer._
        0 * _
    }

    def 'the two-argument overload dumps without dimming'() {
        GraphDumpWriter writer = Spy(constructorArgs: [filer, options, dotRenderer])
        def include = { true }

        when:
        writer.dump(ctx, 'full', include)

        then:
        1 * writer.dump(ctx, 'full', _, false, false)
        1 * writer._
        0 * _
    }

    def 'when debug graphs are off, nothing is dumped'() {
        GraphDumpWriter writer = new GraphDumpWriter(filer, options, dotRenderer)
        seedOneVertex()
        ctx.graph = graph

        when:
        writer.dump(ctx, 'full', { true }, false, false)

        then:
        options.debugGraphs >> false
        0 * filer._
        0 * dotRenderer._
    }

    def 'an empty graph is skipped'() {
        GraphDumpWriter writer = new GraphDumpWriter(filer, options, dotRenderer)
        ctx.graph = graph

        when:
        writer.dump(ctx, 'full', { true }, false, false)

        then:
        options.debugGraphs >> true
        0 * filer._
        0 * dotRenderer._
    }

    def 'a mapper already carrying an error is skipped'() {
        GraphDumpWriter writer = new GraphDumpWriter(filer, options, dotRenderer)
        seedOneVertex()
        ctx.graph = graph
        ctx.report(Diagnostic.error(Subjects.none(), 'no plan for tgt[]'))

        when:
        writer.dump(ctx, 'full', { true }, false, false)

        then:
        options.debugGraphs >> true
        0 * filer._
        0 * dotRenderer._
    }

    def 'a satisfiable, non-empty graph is rendered and written through the Filer'() {
        GraphDumpWriter writer = new GraphDumpWriter(filer, options, dotRenderer)
        seedOneVertex()
        ctx.graph = graph
        FileObject resource = Mock()
        def bytes = new ByteArrayOutputStream()

        when:
        writer.dump(ctx, 'full', { true }, false, false)

        then:
        options.debugGraphs >> true
        1 * dotRenderer.render(_, scope.encode(), _, false) >> 'digraph {}'
        1 * filer.createResource(StandardLocation.SOURCE_OUTPUT, '', _, mapperType) >> resource
        1 * resource.openOutputStream() >> bytes

        expect:
        bytes.toString('UTF-8') == 'digraph {}'
        ctx.diagnostics.empty
    }

    def 'a Filer failure is reported as a transient warning, not propagated'() {
        GraphDumpWriter writer = new GraphDumpWriter(filer, options, dotRenderer)
        seedOneVertex()
        ctx.graph = graph

        when:
        writer.dump(ctx, 'full', { true }, false, false)

        then:
        options.debugGraphs >> true
        1 * dotRenderer.render(_, _, _, false) >> 'digraph {}'
        1 * filer.createResource(*_) >> { throw new IOException('disk full') }

        expect:
        ctx.diagnostics.size() == 1
        with(ctx.diagnostics[0]) {
            severity == Diagnostic.Severity.WARNING
            !permanent
            message.contains('full')
            message.contains('disk full')
        }
    }

    def 'dimUnreachable greys out vertices ExtractedPlan marks unreachable'() {
        GraphDumpWriter writer = new GraphDumpWriter(filer, options, dotRenderer)
        def root = seedOneVertex()
        ctx.graph = graph
        FileObject resource = Stub {
            openOutputStream() >> new ByteArrayOutputStream()
        }
        boolean dimmedRoot = true

        when:
        writer.dump(ctx, 'full', { true }, true, false)

        then:
        options.debugGraphs >> true
        1 * dotRenderer.render(_, _, _, false) >> { args -> dimmedRoot = args[2].test(root); 'digraph {}' }
        1 * filer.createResource(*_) >> resource

        expect:
        !dimmedRoot
    }

    def 'slice: keeps only vertices in-scope and passing the include predicate, with edges between kept vertices'() {
        MapperGraph g = new MapperGraph()
        Scope inScope = new HarnessScope('a()')
        Scope otherScope = new HarnessScope('b()')
        def kept = g.valueFor(inScope, new SourceLocation(AccessPath.of('kept')), STRING, Nullability.NON_NULL)
        def excluded = g.valueFor(inScope, new SourceLocation(AccessPath.of('excluded')), STRING, Nullability.NON_NULL)
        def elsewhere = g.valueFor(otherScope, new SourceLocation(AccessPath.of('elsewhere')), STRING, Nullability.NON_NULL)
        def op = g.apply(new AddOperation('build', Stub(Codegen), 1, false,
                [new PortBinding(new Port('p', STRING, Nullability.NON_NULL),
                        new AddValue(inScope, new SourceLocation(AccessPath.of('kept')), STRING, Nullability.NON_NULL))],
                new AddValue(inScope, new TargetLocation(TargetPath.of('')), STRING, Nullability.NON_NULL),
                Optional.empty(), [] as Set, []))
        def output = g.outputOf(op).get()
        def sliced = dumpWriter.slice(g, inScope) { it != excluded }

        expect:
        sliced.vertexSet() == [kept, op, output] as Set
        sliced.edgeSet().size() == 2
        !sliced.vertexSet().contains(excluded)
        !sliced.vertexSet().contains(elsewhere)
    }

    def 'orderedScopes: distinct scopes passing the include predicate, sorted by encode()'() {
        MapperGraph g = new MapperGraph()
        Scope b = new HarnessScope('b()')
        Scope a = new HarnessScope('a()')
        g.valueFor(a, new SourceLocation(AccessPath.of('x')), STRING, Nullability.NON_NULL)
        g.valueFor(a, new SourceLocation(AccessPath.of('y')), STRING, Nullability.NON_NULL)
        g.valueFor(b, new SourceLocation(AccessPath.of('z')), STRING, Nullability.NON_NULL)
        def excluded = g.valueFor(a, new SourceLocation(AccessPath.of('excl')), STRING, Nullability.NON_NULL)

        expect:
        dumpWriter.orderedScopes(g) { it != excluded } == [a, b]
    }

    def 'infixes: a lone method scope keeps its plain method name'() {
        MethodScope m = new MethodScope(method('foo'))

        expect:
        dumpWriter.infixes([m]) == [(m): 'foo']
    }

    def 'infixes: two method scopes sharing a name are disambiguated with -0/-1'() {
        MethodScope a = new MethodScope(method('foo'))
        MethodScope b = new MethodScope(method('foo'))

        expect:
        dumpWriter.infixes([a, b]) == [(a): 'foo-0', (b): 'foo-1']
    }

    def 'infixesWithinGroup: a single-member group is not disambiguated'() {
        MethodScope m = new MethodScope(method('bar'))

        expect:
        dumpWriter.infixesWithinGroup('bar', [m]) == [(m): 'bar']
    }

    def 'baseInfix: a method scope is named after its method'() {
        MethodScope m = new MethodScope(method('baz'))

        expect:
        dumpWriter.baseInfix(m) == 'baz'
    }

    def 'baseInfix: a non-method scope is named after its enclosing method, suffixed -elem'() {
        MethodScope enclosing = new MethodScope(method('outer'))
        Scope child = childOf(enclosing)

        expect:
        dumpWriter.baseInfix(child) == 'outer-elem'
    }

    def 'enclosingMethodInfix: a method scope resolves to its own method name'() {
        MethodScope m = new MethodScope(method('direct'))

        expect:
        dumpWriter.enclosingMethodInfix(m) == 'direct'
    }

    def 'enclosingMethodInfix: a scope with no method ancestor falls back to "scope"'() {
        expect:
        dumpWriter.enclosingMethodInfix(new HarnessScope('rootless')) == 'scope'
    }

    def 'enclosingMethodInfix: walks up through intermediate scopes to the enclosing method'() {
        MethodScope enclosing = new MethodScope(method('walked'))
        Scope grandchild = childOf(childOf(enclosing))

        expect:
        dumpWriter.enclosingMethodInfix(grandchild) == 'walked'
    }

    /** A one-operation, one-source graph whose target {@code root} is reachable, returning it. */
    private seedOneVertex() {
        def root = graph.valueFor(scope, new TargetLocation(TargetPath.of('')), STRING, Nullability.NON_NULL)
        graph.markReturnRoot(root)
        graph.apply(new AddOperation('new Thing', Stub(Codegen), 1, false,
                [new PortBinding(new Port('p', STRING, Nullability.NON_NULL),
                        new AddValue(scope, new SourceLocation(AccessPath.of('p')), STRING, Nullability.NON_NULL))],
                new AddValue(scope, new TargetLocation(TargetPath.of('')), STRING, Nullability.NON_NULL),
                Optional.empty(), [] as Set, []))
        root
    }

    private ExecutableElement method(final String name) {
        Stub(ExecutableElement) {
            getSimpleName() >> Stub(Name) { toString() >> name }
            getParameters() >> []
        }
    }

    /** A minimal non-method scope whose only role is to point at {@code parentScope}. */
    private Scope childOf(final Scope parentScope) {
        new Scope() {
            String encode() { 'child' }

            Optional<Scope> parent() { Optional.of(parentScope) }
        }
    }
}
