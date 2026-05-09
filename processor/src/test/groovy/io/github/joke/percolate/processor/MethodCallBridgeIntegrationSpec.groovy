package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

@Tag('integration')
class MethodCallBridgeIntegrationSpec extends Specification {

    def 'sibling-method conversion produces expected realised subgraph'() {
        given:
        def source = JavaFileObjects.forSourceString('test.SiblingMethodMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            class Person {
                public Address getAddress() { return new Address(); }
            }

            class Address {
                public String getStreet() { return "Main St"; }
                public String getCity() { return "NYC"; }
            }

            class HumanAddress {
                private final String street;
                private final String city;

                public HumanAddress(String street, String city) {
                    this.street = street;
                    this.city = city;
                }
            }

            class Human {
                private final HumanAddress address;

                public Human(HumanAddress address) {
                    this.address = address;
                }
            }

            @Mapper
            public interface SiblingMethodMapper {
                HumanAddress mapAddress(Address address);

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
        compilation.status() == Compilation.Status.SUCCESS
        def diagnostics = compilation.diagnostics()
        def errors = diagnostics.findAll { it.kind == Kind.ERROR }
        errors.isEmpty()
    }

    def 'chained mapping produces the chain in realised subgraph'() {
        given:
        def source = JavaFileObjects.forSourceString('test.ChainedMappingMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            class GoldenRetriever {
                public String getName() { return "Buddy"; }
            }

            class BigDog {
                private final String name;

                public BigDog(String name) {
                    this.name = name;
                }
            }

            class Dog {
                private final String name;

                public Dog(String name) {
                    this.name = name;
                }
            }

            class Pet {
                private final String name;

                public Pet(String name) {
                    this.name = name;
                }
            }

            @Mapper
            public interface ChainedMappingMapper {
                BigDog map(GoldenRetriever goldenRetriever);
                Dog map(BigDog bigDog);
                Pet map(Dog dog);

                @Map(target = "name", source = "goldenRetriever.name")
                Pet getPet(GoldenRetriever goldenRetriever);
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

    def 'golden DOT for expanded mapper with both direct and chain method calls'() {
        given:
        def source = JavaFileObjects.forSourceString('test.DotGoldenMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            class Person {
                public String getName() { return "John"; }
                public Address getAddress() { return new Address(); }
            }

            class Address {
                public String getStreet() { return "Main St"; }
            }

            class HumanAddress {
                private final String street;

                public HumanAddress(String street) {
                    this.street = street;
                }
            }

            class Human {
                private final String name;
                private final HumanAddress address;

                public Human(String name, HumanAddress address) {
                    this.name = name;
                    this.address = address;
                }
            }

            @Mapper
            public interface DotGoldenMapper {
                HumanAddress mapAddress(Address address);

                @Map(target = "name", source = "person.name")
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
        compilation.status() == Compilation.Status.SUCCESS
        def diagnostics = compilation.diagnostics()
        def errors = diagnostics.findAll { it.kind == Kind.ERROR }
        errors.isEmpty()

        // DOT files should have been generated
        def generatedFiles = compilation.generatedSourceFiles()
        def dotFiles = generatedFiles.findAll { it.name.toString().endsWith('.dot') }
        dotFiles.size() >= 1

        // DOT file should contain node and edge definitions
        def dotContent = dotFiles[0].getCharContent(true).toString()
        dotContent.contains('digraph')
    }
}
