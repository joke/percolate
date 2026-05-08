package io.github.joke.percolate.processor.stages.seed
import io.github.joke.percolate.processor.TestCompilers
import io.github.joke.percolate.processor.PercolateProcessor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

@Tag('integration')
class SeedSharedPrefixIsDeduplicatedSpec extends Specification {

    def 'two directives with shared prefix do not duplicate nodes'() {
        given:
        def source = JavaFileObjects.forSourceString('test.SharedPrefixMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            class Input {
                String getStreet() { return "Main St"; }
                String getCity() { return "NYC"; }
            }

            class Output {
                final String street;
                final String city;
                Output(String street, String city) {
                    this.street = street;
                    this.city = city;
                }
            }

            @Mapper
            public interface SharedPrefixMapper {
                @Map(target = "street", source = "input.street")
                @Map(target = "city", source = "input.city")
                Output map(Input input);
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
        errors.size() == 0
    }
}
