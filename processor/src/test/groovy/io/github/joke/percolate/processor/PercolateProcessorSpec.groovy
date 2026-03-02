package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class PercolateProcessorSpec extends Specification {

    def "processor compiles @Mapper interface without errors"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.SimpleMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '',
            '@Mapper',
            'public interface SimpleMapper {',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper)

        then:
        assertThat(compilation).succeeded()
    }

    def "exportDot writes binding and wiring dot files to /tmp"() {
        given:
        def src = JavaFileObjects.forSourceLines('test.Src',
            'package test;',
            'public class Src { public String getName() { return ""; } }')
        def tgt = JavaFileObjects.forSourceLines('test.Tgt',
            'package test;',
            'public class Tgt { private final String name;',
            '    public Tgt(String name) { this.name = name; }',
            '    public String getName() { return name; } }')
        def mapper = JavaFileObjects.forSourceLines('test.DotMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper public interface DotMapper { Tgt map(Src src); }')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(src, tgt, mapper)

        then:
        assertThat(compilation).succeeded()
        new File('/tmp/DotMapper-map-binding.dot').text.contains('digraph')
        new File('/tmp/DotMapper-map-wiring.dot').text.contains('digraph')
    }
}
