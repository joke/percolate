package io.github.joke.percolate.stage

import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class BindingStageSpec extends Specification {

    def "single-param method with explicit @Map directive compiles without error"() {
        given:
        def source = JavaFileObjects.forSourceLines('test.Source',
            'package test;',
            'public class Source { public String getName() { return ""; } }')
        def target = JavaFileObjects.forSourceLines('test.Target',
            'package test;',
            'public class Target { private final String name;',
            '    public Target(String name) { this.name = name; } }')
        def mapper = JavaFileObjects.forSourceLines('test.MyMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper public interface MyMapper {',
            '    @Map(target = "name", source = "name")',
            '    Target map(Source source);',
            '}')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(source, target, mapper)

        then:
        assertThat(compilation).succeeded()
    }

    def "multi-param method with param-prefixed source paths compiles without error"() {
        given:
        def a = JavaFileObjects.forSourceLines('test.A',
            'package test;',
            'public class A { public String getFoo() { return ""; } }')
        def b = JavaFileObjects.forSourceLines('test.B',
            'package test;',
            'public class B { public String getBar() { return ""; } }')
        def target = JavaFileObjects.forSourceLines('test.Out',
            'package test;',
            'public class Out { private final String foo; private final String bar;',
            '    public Out(String foo, String bar) { this.foo = foo; this.bar = bar; } }')
        def mapper = JavaFileObjects.forSourceLines('test.MultiMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper public interface MultiMapper {',
            '    @Map(target = "foo", source = "a.foo")',
            '    @Map(target = "bar", source = "b.bar")',
            '    Out map(A a, B b);',
            '}')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(a, b, target, mapper)

        then:
        assertThat(compilation).succeeded()
    }
}
