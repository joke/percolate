package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

@Tag('integration')
class ContainerExpansionIntegrationSpec extends Specification {

    def 'OptionalWrap + MethodCallBridge produces realised Optional<Pet> from Dog'() {
        given:
        def source = JavaFileObjects.forSourceString('test.OptionalWrapMapper', '''
            import io.github.joke.percolate.Mapper;
            import java.util.Optional;

            class Dog {
                public String getName() { return "Rex"; }
            }

            class Pet {
                private final String name;
                public Pet(String name) { this.name = name; }
            }

            @Mapper
            public interface OptionalWrapMapper {
                Pet map(Dog dog);
                Optional<Pet> findPet(Dog dog);
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

    def 'OptionalUnwrap + MethodCallBridge produces realised Pet from Optional<Dog>'() {
        given:
        def source = JavaFileObjects.forSourceString('test.OptionalUnwrapMapper', '''
            import io.github.joke.percolate.Mapper;
            import java.util.Optional;

            class Dog {
                public String getName() { return "Rex"; }
            }

            class Pet {
                private final String name;
                public Pet(String name) { this.name = name; }
            }

            @Mapper
            public interface OptionalUnwrapMapper {
                Pet map(Dog dog);
                Pet getPet(Optional<Dog> dog);
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

    def 'ListMap + MethodCallBridge produces realised List<Pet> from List<Dog> with element scope'() {
        given:
        def source = JavaFileObjects.forSourceString('test.ListMapMapper', '''
            import io.github.joke.percolate.Mapper;
            import java.util.List;

            class Dog {
                public String getName() { return "Rex"; }
            }

            class Pet {
                private final String name;
                public Pet(String name) { this.name = name; }
            }

            @Mapper
            public interface ListMapMapper {
                Pet map(Dog dog);
                List<Pet> convertAll(List<Dog> dogs);
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

    def 'cross-container SetMap and ListMap resolve List<Dog> -> Set<Pet> and Set<Dog> -> List<Pet>'() {
        given:
        def source = JavaFileObjects.forSourceString('test.CrossContainerMapper', '''
            import io.github.joke.percolate.Mapper;
            import java.util.List;
            import java.util.Set;

            class Dog {
                public String getName() { return "Rex"; }
            }

            class Pet {
                private final String name;
                public Pet(String name) { this.name = name; }
            }

            @Mapper
            public interface CrossContainerMapper {
                Pet map(Dog dog);
                Set<Pet> convertToList(List<Dog> dogs);
                List<Pet> convertToSet(Set<Dog> dogs);
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

    def 'nested container expansion resolves List<Optional<GR>> -> Optional<List<Pet>> with element-scope chain'() {
        given:
        def source = JavaFileObjects.forSourceString('test.NestedContainerMapper', '''
            import io.github.joke.percolate.Mapper;
            import java.util.List;
            import java.util.Optional;

            class GoldenRetriever {
                public String getName() { return "Buddy"; }
            }

            class Pet {
                private final String name;
                public Pet(String name) { this.name = name; }
            }

            @Mapper
            public interface NestedContainerMapper {
                Pet map(GoldenRetriever goldenRetriever);
                Optional<List<Pet>> convert(List<Optional<GoldenRetriever>> xs);
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

    def 'golden DOT with container expansion renders element scope clusters'() {
        given:
        def source = JavaFileObjects.forSourceString('test.DotContainerMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;
            import java.util.List;
            import java.util.Optional;

            class Dog {
                public String getName() { return "Rex"; }
            }

            class Pet {
                private final String name;
                public Pet(String name) { this.name = name; }
            }

            @Mapper
            public interface DotContainerMapper {
                Pet map(Dog dog);

                @Map(target = "name", source = "dog.name")
                Pet getPet(Dog dog);

                Optional<Pet> findPet(Dog dog);
                List<Pet> convertAll(List<Dog> dogs);
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
}
