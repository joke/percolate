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

    def "wildcard source expands all properties of the param type"() {
        given:
        def order = JavaFileObjects.forSourceLines('test.Order',
            'package test;',
            'public class Order {',
            '    public long getOrderId() { return 0L; }',
            '    public long getOrderNumber() { return 0L; }',
            '}')
        def out = JavaFileObjects.forSourceLines('test.Out',
            'package test;',
            'public class Out {',
            '    private final long orderId; private final long orderNumber;',
            '    public Out(long orderId, long orderNumber) {',
            '        this.orderId = orderId; this.orderNumber = orderNumber;',
            '    }',
            '}')
        def mapper = JavaFileObjects.forSourceLines('test.WildMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper; import io.github.joke.percolate.Map;',
            '@Mapper public interface WildMapper {',
            '    @Map(target = ".", source = "order.*")',
            '    Out map(Order order);',
            '}')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(order, out, mapper)

        then:
        assertThat(compilation).succeeded()
    }

    def "same-name matching fills unmapped slots automatically"() {
        given:
        def src = JavaFileObjects.forSourceLines('test.Src',
            'package test;',
            'public class Src { public String getName() { return ""; } public int getAge() { return 0; } }')
        def tgt = JavaFileObjects.forSourceLines('test.Tgt',
            'package test;',
            'public class Tgt { private final String name; private final int age;',
            '    public Tgt(String name, int age) { this.name = name; this.age = age; }',
            '    public String getName() { return name; }',
            '    public int getAge() { return age; } }')
        def mapper = JavaFileObjects.forSourceLines('test.AutoMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper public interface AutoMapper { Tgt map(Src src); }')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(src, tgt, mapper)

        then:
        assertThat(compilation).succeeded()
    }
}
