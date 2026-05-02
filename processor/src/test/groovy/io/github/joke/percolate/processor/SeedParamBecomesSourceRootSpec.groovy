package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

import static com.google.testing.compile.Compiler.javac

@Tag('integration')
class SeedParamBecomesSourceRootSpec extends Specification {

    def 'parameter becomes a source-root node in the seeded graph'() {
        given:
        def source = JavaFileObjects.forSourceString('test.ParamSourceRootMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface ParamSourceRootMapper {
                @Map(target = "name", source = "input")
                Object map(Object input);
            }
        ''')

        when:
        Compilation compilation = javac()
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
