package io.github.joke.percolate.processor.stages.seed
import io.github.joke.percolate.processor.TestCompilers
import io.github.joke.percolate.processor.PercolateProcessor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

@Tag('integration')
class SeedDottedSourceBecomesChainSpec extends Specification {

    def 'dotted source produces a multi-segment source chain'() {
        given:
        def source = JavaFileObjects.forSourceString('test.DottedSourceChainMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            class Address {
                String getStreet() { return "Main St"; }
            }

            class Input {
                Address getAddress() { return new Address(); }
            }

            class Output {
                final String street;
                Output(String street) { this.street = street; }
            }

            @Mapper
            public interface DottedSourceChainMapper {
                @Map(target = "street", source = "input.address.street")
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
