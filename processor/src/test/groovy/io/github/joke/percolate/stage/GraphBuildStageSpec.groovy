package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class GraphBuildStageSpec extends Specification {

    def "builds graph for single-param mapper without errors"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.PersonMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface PersonMapper {',
            '    PersonDto map(Person person);',
            '}',
        )
        def person = JavaFileObjects.forSourceLines('test.Person',
            'package test;',
            'public class Person {',
            '    public String getName() { return ""; }',
            '}',
        )
        def personDto = JavaFileObjects.forSourceLines('test.PersonDto',
            'package test;',
            'public class PersonDto {',
            '    public final String name;',
            '    public PersonDto(String name) { this.name = name; }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, person, personDto)

        then:
        assertThat(compilation).succeeded()
    }
}
