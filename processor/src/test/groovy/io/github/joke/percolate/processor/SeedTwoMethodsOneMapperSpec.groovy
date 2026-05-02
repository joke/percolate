package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

import static com.google.testing.compile.Compiler.javac

@Tag('integration')
class SeedTwoMethodsOneMapperSpec extends Specification {

    def 'two methods on the same mapper produce two method-scoped subgraphs'() {
        given:
        def source = JavaFileObjects.forSourceString('test.TwoMethodsOneMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface TwoMethodsOneMapper {
                @Map(target = "name", source = "input1")
                Object map(Object input1);

                @Map(target = "name", source = "input2")
                Object map2(Object input2);
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
