package io.github.joke.percolate.processor.stages.seed
import io.github.joke.percolate.processor.TestCompilers
import io.github.joke.percolate.processor.PercolateProcessor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

@Tag('integration')
class SeedParamBecomesSourceRootSpec extends Specification {

    def 'parameter becomes a source-root node in the seeded graph'() {
        given:
        def source = JavaFileObjects.forSourceString('test.ParamSourceRootMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            class Input {
                String getValue() { return "test"; }
            }

            class Output {
                final String name;
                Output(String name) { this.name = name; }
            }

            @Mapper
            public interface ParamSourceRootMapper {
                @Map(target = "name", source = "input.value")
                Output map(Input input);
            }
        ''')

        when:
        Compilation compilation = TestCompilers.compiler()
                .withProcessors(new PercolateProcessor())
                .withOptions('-Apercolate.debug.graphs=true')
                .compile(source)

        then:
        if (compilation.status() != Compilation.Status.SUCCESS) {
            println "Compilation failed: " + compilation.diagnostics().findAll { it.kind == Kind.ERROR || it.kind == Kind.WARNING }
        }
        compilation.status() == Compilation.Status.SUCCESS
        def diagnostics = compilation.diagnostics()
        def errors = diagnostics.findAll { it.kind == Kind.ERROR }
        errors.size() == 0
    }
}
