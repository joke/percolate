package io.github.joke.percolate.processor.stages.seed

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import io.github.joke.percolate.processor.TestCompilers
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

@Tag('integration')
class SeedDirectiveSeedsChainSpec extends Specification {

    def 'a @Map directive seeds source chain, target chain, and bridging edge'() {
        given:
        def source = JavaFileObjects.forSourceString('test.DirectiveChainMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            class Input {
                String getName() { return "test"; }
                int getAge() { return 0; }
            }

            class Output {
                final String firstName;
                final int age;
                Output(String firstName, int age) {
                    this.firstName = firstName;
                    this.age = age;
                }
            }

            @Mapper
            public interface DirectiveChainMapper {
                @Map(target = "firstName", source = "input.name")
                @Map(target = "age", source = "input.age")
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
