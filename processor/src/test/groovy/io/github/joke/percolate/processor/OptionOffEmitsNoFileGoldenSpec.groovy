package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

@Tag('integration')
class OptionOffEmitsNoFileGoldenSpec extends Specification {

    def 'option-off emits no .seed.dot file'() {
        given:
        def source = JavaFileObjects.forSourceString('test.NoDebugMapper', '''
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
            public interface NoDebugMapper {
                @Map(target = "name", source = "input.value")
                Output map(Input input);
            }
        ''')

        when:
        Compilation compilation = TestCompilers.compiler()
                .withProcessors(new PercolateProcessor())
                .compile(source)

        then:
        compilation.status() == Compilation.Status.SUCCESS
        def diagnostics = compilation.diagnostics()
        def errors = diagnostics.findAll { it.kind == Kind.ERROR }
        errors.size() == 0
    }
}
