package io.github.joke.percolate.processor.stages.seed

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import io.github.joke.percolate.processor.TestCompilers
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

@Tag('integration')
class SeedDottedTargetBecomesChainSpec extends Specification {

    def 'dotted target produces a multi-segment target chain'() {
        given:
        def source = JavaFileObjects.forSourceString('test.DottedTargetChainMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            class Input {
                String getName() { return "test"; }
            }

            class Output {
                final String address;
                Output(String address) { this.address = address; }
            }

            @Mapper
            public interface DottedTargetChainMapper {
                @Map(target = "address", source = "input.name")
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
