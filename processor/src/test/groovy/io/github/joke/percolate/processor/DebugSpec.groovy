package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

import static com.google.testing.compile.Compiler.javac

@Tag('integration')
class DebugSpec extends Specification {

    def 'debug v1 demo'() {
        given:
        def personSource = JavaFileObjects.forSourceString('test.Person', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            class Person {
                public String getLastName() { return "Doe"; }
                public String getFirst() { return "John"; }
            }
        ''')
        def humanSource = JavaFileObjects.forSourceString('test.Human', '''
            package test;

            class Human {
                private final String firstName;
                private final String lastName;

                public Human(String firstName, String lastName) {
                    this.firstName = firstName;
                    this.lastName = lastName;
                }
            }
        ''')
        def mapperSource = JavaFileObjects.forSourceString('test.PersonMapper', '''
            package test;

            @Mapper
            interface PersonMapper {
                @Map(target = "lastName", source = "person.lastName")
                @Map(target = "firstName", source = "person.first")
                Human map(Person person);
            }
        ''')

        when:
        Compilation compilation = javac()
                .withProcessors(new PercolateProcessor())
                .withOptions('-Apercolate.debug.graphs=true')
                .compile(personSource, humanSource, mapperSource)

        then:
        println "Status: ${compilation.status()}"
        compilation.diagnostics().each { diag ->
            println "  ${diag.kind}: ${diag.getMessage(null)} at line ${diag.getLineNumber()}"
        }
    }
}
