package io.github.joke.percolate.processor.stages.expand

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic

@Tag('integration')
class NoSilentSourcingEndToEndSpec extends Specification {

    def 'a constructor with an undeclared parameter is rejected and leaves the target unrealised'() {
        given: 'Target has only a two-arg constructor, but only `a` is mapped'
        def source = JavaFileObjects.forSourceLines('test.Source',
                'package test;',
                'public final class Source {',
                '    private final String a;',
                '    public Source(String a) { this.a = a; }',
                '    public String getA() { return a; }',
                '}')
        def target = JavaFileObjects.forSourceLines('test.Target',
                'package test;',
                'public final class Target {',
                '    private final String a;',
                '    private final String b;',
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
                '    @Map(target = "a", source = "src.a")',
                '    Target map(Source src);',
                '}')

        when:
        Compilation compilation = Compiler.javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)

        then: 'the gate rejects the 2-arg constructor (param set {a,b} != declared {a}); no vacuous fallback fires'
        def errors = compilation.diagnostics().findAll { it.kind == Diagnostic.Kind.ERROR }
        errors.any { it.getMessage(null).contains('no plan') && it.getMessage(null).contains('test.Target') }
    }
}
