package io.github.joke.percolate.stage

import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class MapperDiscoveryStageSpec extends Specification {

    def "emits error when @Mapper applied to a class"() {
        given:
        def badMapper = JavaFileObjects.forSourceLines('test.BadMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public class BadMapper {}')

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(badMapper)

        then:
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining('@Mapper can only be applied to interfaces')
    }
}
