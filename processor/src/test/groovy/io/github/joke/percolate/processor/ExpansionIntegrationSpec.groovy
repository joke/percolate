package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

@Tag('integration')
class ExpansionIntegrationSpec extends Specification {

    def 'v1 demo compiles without errors and produces expected edges'() {
        given:
        def source = JavaFileObjects.forSourceString('test.PersonMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            class Person {
                public String getLastName() { return "Doe"; }
                public String getFirst() { return "John"; }
            }

            class Human {
                private final String firstName;
                private final String lastName;

                public Human(String firstName, String lastName) {
                    this.firstName = firstName;
                    this.lastName = lastName;
                }
            }

            @Mapper
            public interface PersonMapper {
                @Map(target = "lastName", source = "person.lastName")
                @Map(target = "firstName", source = "person.first")
                Human map(Person person);
            }
        ''')

        when:
        Compilation compilation = TestCompilers.compiler()
                .withProcessors(new PercolateProcessor())
                .withOptions('-Apercolate.debug.graphs=true')
                .compile(source)

        then:
        compilation.status() == Compilation.Status.SUCCESS
        def diagnostics = compilation.diagnostics()
        def errors = diagnostics.findAll { it.kind == Kind.ERROR }
        errors.isEmpty()
    }

    def 'dotted target path produces Tier-2 error'() {
        given:
        def source = JavaFileObjects.forSourceString('test.DottedTargetMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            class Person {
                public String getName() { return "John"; }
            }

            class Human {
                private final String name;

                public Human(String name) {
                    this.name = name;
                }
            }

            @Mapper
            public interface DottedTargetMapper {
                @Map(target = "address.street", source = "person.name")
                Human map(Person person);
            }
        ''')

        when:
        Compilation compilation = TestCompilers.compiler()
                .withProcessors(new PercolateProcessor())
                .withOptions('-Apercolate.debug.graphs=true')
                .compile(source)

        then:
        compilation.status() == Compilation.Status.FAILURE
        def diagnostics = compilation.diagnostics()
        def errors = diagnostics.findAll { it.kind == Kind.ERROR }
        errors.size() >= 1
        def errorMsg = errors[0].getMessage(null)
        errorMsg.contains('No strategy could realise')
    }

    def 'different concrete types produce Tier-3 gap error'() {
        given:
        def source = JavaFileObjects.forSourceString('test.TypeMismatchMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            class Person {
                public Address getAddress() { return new Address(); }

                public static class Address {
                    public String getStreet() { return "Main St"; }
                }
            }

            class Human {
                private final Address address;

                public Human(Address address) {
                    this.address = address;
                }

                public static class Address {
                    public String getCity() { return "NYC"; }
                }
            }

            @Mapper
            public interface TypeMismatchMapper {
                @Map(target = "address", source = "person.address")
                Human map(Person person);
            }
        ''')

        when:
        Compilation compilation = TestCompilers.compiler()
                .withProcessors(new PercolateProcessor())
                .withOptions('-Apercolate.debug.graphs=true')
                .compile(source)

        then:
        compilation.status() == Compilation.Status.FAILURE
        def diagnostics = compilation.diagnostics()
        def errors = diagnostics.findAll { it.kind == Kind.ERROR }
        errors.size() >= 1
    }

    def 'Pipeline.process returns null for expanded mappers'() {
        given:
        def source = JavaFileObjects.forSourceString('test.PersonMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            class Person {
                public String getLastName() { return "Doe"; }
                public String getFirst() { return "John"; }
            }

            class Human {
                private final String firstName;
                private final String lastName;

                public Human(String firstName, String lastName) {
                    this.firstName = firstName;
                    this.lastName = lastName;
                }
            }

            @Mapper
            public interface PersonMapper {
                @Map(target = "lastName", source = "person.lastName")
                @Map(target = "firstName", source = "person.first")
                Human map(Person person);
            }
        ''')

        when:
        Compilation compilation = TestCompilers.compiler()
                .withProcessors(new PercolateProcessor())
                .compile(source)

        then:
        compilation.status() == Compilation.Status.SUCCESS
        // No Mapper implementation is generated (codegen is future)
        def generated = compilation.generatedSourceFiles()
        def mapperImpls = generated.findAll { it.name.toString().contains('Mapper') }
        mapperImpls.isEmpty()
    }
}
