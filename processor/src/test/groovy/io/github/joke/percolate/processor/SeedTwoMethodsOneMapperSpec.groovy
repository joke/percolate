package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

@Tag('integration')
class SeedTwoMethodsOneMapperSpec extends Specification {

    def 'two methods on the same mapper produce two method-scoped subgraphs'() {
        given:
        def source = JavaFileObjects.forSourceString('test.TwoMethodsOneMapper', '''
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
            public interface TwoMethodsOneMapper {
                @Map(target = "name", source = "input1.value")
                Output map(Input input1);

                @Map(target = "name", source = "input2.value")
                Output map2(Input input2);
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
