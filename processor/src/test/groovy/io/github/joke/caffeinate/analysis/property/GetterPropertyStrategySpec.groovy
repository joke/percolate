package io.github.joke.caffeinate.analysis.property

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.caffeinate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class GetterPropertyStrategySpec extends Specification {

    def "compiles cleanly when source has only getter-based properties"() {
        given:
        def personSrc = JavaFileObjects.forSourceLines("io.example.Person",
            "package io.example;",
            "public class Person {",
            "    private final String name;",
            "    public Person(String name) { this.name = name; }",
            "    public String getName() { return name; }",
            "}")
        def mapperSrc = JavaFileObjects.forSourceLines("io.example.PersonMapper",
            "package io.example;",
            "import io.github.joke.caffeinate.Mapper;",
            "@Mapper",
            "public interface PersonMapper {",
            "    Person map(Person source);",
            "}")

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(personSrc, mapperSrc)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation).generatedSourceFile("io.example.PersonMapperImpl")
    }
}
