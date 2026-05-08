package io.github.joke.percolate.processor.stages.seed
import io.github.joke.percolate.processor.TestCompilers
import io.github.joke.percolate.processor.PercolateProcessor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

@Tag('integration')
class SeedInheritedGenericMethodSpec extends Specification {

    def 'inherited generic method is seeded the same as a directly declared method'() {
        given:
        def source = JavaFileObjects.forSourceString('test.InheritedGenericMapper', '''
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
            public interface InheritedGenericMapper extends BaseMapper {
            }

            interface BaseMapper {
                @Map(target = "name", source = "value.value")
                Output map(Input value);
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
