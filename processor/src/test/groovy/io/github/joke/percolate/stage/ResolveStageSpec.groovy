package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class ResolveStageSpec extends Specification {

    def "auto-matches same-name properties for single-param method"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.AutoMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface AutoMapper {',
            '    Target map(Source source);',
            '}',
        )
        def source = JavaFileObjects.forSourceLines('test.Source',
            'package test;',
            'public class Source {',
            '    public String getName() { return ""; }',
            '    public int getAge() { return 0; }',
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
        assertThat(compilation).succeeded()
    }
}
