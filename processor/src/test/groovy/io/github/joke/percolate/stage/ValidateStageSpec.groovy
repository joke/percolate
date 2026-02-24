package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class ValidateStageSpec extends Specification {

    def "emits error when target property has no source mapping"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.BadMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface BadMapper {',
            '    Target map(Source source);',
            '}',
        )
        def source = JavaFileObjects.forSourceLines('test.Source',
            'package test;',
            'public class Source {',
            '    public String getName() { return ""; }',
            '}',
        )
        def target = JavaFileObjects.forSourceLines('test.Target',
            'package test;',
            'public class Target {',
            '    public final String name;',
            '    public final int age;',
            '    public Target(String name, int age) {',
            '        this.name = name;',
            '        this.age = age;',
            '    }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, source, target)

        then:
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining('age')
    }

    def "emits error when converter method is missing for type mismatch"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.MissingConverterMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper',
            'public interface MissingConverterMapper {',
            '    @Map(target = "venue", source = "venue")',
            '    FlatOrder map(Order order);',
            '}',
        )
        def venue = JavaFileObjects.forSourceLines('test.Venue',
            'package test;',
            'public class Venue {',
            '    public String getName() { return ""; }',
            '}',
        )
        def flatVenue = JavaFileObjects.forSourceLines('test.FlatVenue',
            'package test;',
            'public class FlatVenue {',
            '    public final String name;',
            '    public FlatVenue(String name) { this.name = name; }',
            '}',
        )
        def order = JavaFileObjects.forSourceLines('test.Order',
            'package test;',
            'public class Order {',
            '    public Venue getVenue() { return null; }',
            '}',
        )
        def flatOrder = JavaFileObjects.forSourceLines('test.FlatOrder',
            'package test;',
            'public class FlatOrder {',
            '    public final FlatVenue venue;',
            '    public FlatOrder(FlatVenue venue) { this.venue = venue; }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, venue, flatVenue, order, flatOrder)

        then:
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining('venue')
    }
}
