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
}
