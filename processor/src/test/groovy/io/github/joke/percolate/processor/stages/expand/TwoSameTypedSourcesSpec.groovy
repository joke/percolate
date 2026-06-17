package io.github.joke.percolate.processor.stages.expand

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification
import spock.lang.Tag

/**
 * Two same-typed sources must not cross-bind: each slot's directive {@code SourceLocation} pins its own source, so
 * the pull engine never lets "any String in scope" shadow the directive's chosen one (design D3/D4). With the
 * curated per-demand candidate list gone, this is the guard that the demanded source location — not type matching —
 * carries the directive's selection.
 */
@Tag('integration')
class TwoSameTypedSourcesSpec extends Specification {

    def 'each slot binds the source its directive pins, never a same-typed sibling'() {
        given:
        def source = JavaFileObjects.forSourceLines('test.Source',
                'package test;',
                'public final class Source {',
                '    private final String x; private final String y;',
                '    public Source(String x, String y) { this.x = x; this.y = y; }',
                '    public String getX() { return x; }',
                '    public String getY() { return y; }',
                '}')
        def target = JavaFileObjects.forSourceLines('test.Target',
                'package test;',
                'public final class Target {',
                '    private final String a; private final String b;',
                '    public Target(String a, String b) { this.a = a; this.b = b; }',
                '    public String getA() { return a; }',
                '    public String getB() { return b; }',
                '}')
        def mapper = JavaFileObjects.forSourceLines('test.M',
                'package test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '@Mapper',
                'public interface M {',
                '    @Map(target = "a", source = "in.y")',
                '    @Map(target = "b", source = "in.x")',
                '    Target map(Source in);',
                '}')

        when:
        Compilation compilation = Compiler.javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)

        then: 'a binds in.getY() and b binds in.getX() — the directives are honoured, not cross-bound'
        compilation.errors().empty
        def content = compilation.generatedSourceFile('test.MImpl').get().getCharContent(true).toString()
        content.contains('String v0 = in.getY();')
        content.contains('String v1 = in.getX();')
        content.contains('return new Target(v0, v1);')
    }
}
