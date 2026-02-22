package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class CodeGenStageSpec extends Specification {

    def "generates impl for single-param mapper with name-matched properties"() {
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
            '    public int getAge() { return 0; }',
            '}',
        )
        def personDto = JavaFileObjects.forSourceLines('test.PersonDto',
            'package test;',
            'public class PersonDto {',
            '    public final String name;',
            '    public final int age;',
            '    public PersonDto(String name, int age) {',
            '        this.name = name;',
            '        this.age = age;',
            '    }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, person, personDto)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation)
            .generatedSourceFile('test.PersonMapperImpl')
            .contentsAsUtf8String()
            .contains('implements PersonMapper')
        assertThat(compilation)
            .generatedSourceFile('test.PersonMapperImpl')
            .contentsAsUtf8String()
            .contains('person.getName()')
        assertThat(compilation)
            .generatedSourceFile('test.PersonMapperImpl')
            .contentsAsUtf8String()
            .contains('person.getAge()')
        assertThat(compilation)
            .generatedSourceFile('test.PersonMapperImpl')
            .contentsAsUtf8String()
            .contains('new test.PersonDto(')
    }
}
