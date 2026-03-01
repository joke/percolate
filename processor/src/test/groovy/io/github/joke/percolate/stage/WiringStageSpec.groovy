package io.github.joke.percolate.stage

import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class WiringStageSpec extends Specification {

    def "compatible types — no conversion needed — compiles and generates impl"() {
        given:
        def src = JavaFileObjects.forSourceLines('test.Src',
            'package test;',
            'public class Src { public String getName() { return ""; } }')
        def tgt = JavaFileObjects.forSourceLines('test.Tgt',
            'package test;',
            'public class Tgt { private final String name;',
            '    public Tgt(String name) { this.name = name; }',
            '    public String getName() { return name; } }')
        def mapper = JavaFileObjects.forSourceLines('test.SimpleMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper public interface SimpleMapper { Tgt map(Src src); }')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(src, tgt, mapper)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation).generatedSourceFile('test.SimpleMapperImpl')
    }
}
