package io.github.joke.percolate.stage

import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class RegistrationStageSpec extends Specification {

    def "mapper with abstract single-param method compiles without error"() {
        given:
        def src = JavaFileObjects.forSourceLines('test.Src',
            'package test;',
            'public class Src { public String getName() { return ""; } }')
        def tgt = JavaFileObjects.forSourceLines('test.Tgt',
            'package test;',
            'public class Tgt { private final String name;',
            '    public Tgt(String name) { this.name = name; }',
            '    public String getName() { return name; } }')
        def mapper = JavaFileObjects.forSourceLines('test.MyMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper public interface MyMapper { Tgt map(Src src); }')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(src, tgt, mapper)

        then:
        assertThat(compilation).succeeded()
    }

    def "mapper with default method compiles without error"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.HelpMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper public interface HelpMapper {',
            '    default String helper(String s) { return s.trim(); }',
            '}')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper)

        then:
        assertThat(compilation).succeeded()
    }

    def "mapper with multi-param abstract method compiles without error"() {
        given:
        def a = JavaFileObjects.forSourceLines('test.A',
            'package test;',
            'public class A { public String getFoo() { return ""; } }')
        def b = JavaFileObjects.forSourceLines('test.B',
            'package test;',
            'public class B { public String getBar() { return ""; } }')
        def out = JavaFileObjects.forSourceLines('test.Out',
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
            .compile(a, b, out, mapper)

        then:
        assertThat(compilation).succeeded()
    }
}
