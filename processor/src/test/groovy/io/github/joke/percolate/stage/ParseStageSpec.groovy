package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class ParseStageSpec extends Specification {

    def "parses @Mapper interface with abstract and default methods"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.TestMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '',
            '@Mapper',
            'public interface TestMapper {',
            '    @Map(target = "name", source = "firstName")',
            '    Target map(Source source);',
            '',
            '    default String identity(String s) { return s; }',
            '}',
        )
        def targetClass = JavaFileObjects.forSourceLines('test.Target',
            'package test;',
            'public class Target {',
            '    public final String name;',
            '    public Target(String name) { this.name = name; }',
            '}',
        )
        def sourceClass = JavaFileObjects.forSourceLines('test.Source',
            'package test;',
            'public class Source {',
            '    public String getFirstName() { return ""; }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, targetClass, sourceClass)

        then:
        assertThat(compilation).succeeded()
    }

    def "emits error when @Mapper applied to class"() {
        given:
        def badMapper = JavaFileObjects.forSourceLines('test.BadMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '',
            '@Mapper',
            'public class BadMapper {',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(badMapper)

        then:
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining('@Mapper can only be applied to interfaces')
    }

    def "parses multiple @Map annotations on a method"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.MultiMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '',
            '@Mapper',
            'public interface MultiMapper {',
            '    @Map(target = "name", source = "firstName")',
            '    @Map(target = "age", source = "years")',
            '    Target map(Source source);',
            '}',
        )
        def targetClass = JavaFileObjects.forSourceLines('test.Target',
            'package test;',
            'public class Target {',
            '    public final String name;',
            '    public final int age;',
            '    public Target(String name, int age) { this.name = name; this.age = age; }',
            '}',
        )
        def sourceClass = JavaFileObjects.forSourceLines('test.Source',
            'package test;',
            'public class Source {',
            '    public String getFirstName() { return ""; }',
            '    public int getYears() { return 0; }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, targetClass, sourceClass)

        then:
        assertThat(compilation).succeeded()
    }
}
