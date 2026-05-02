package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

import static com.google.testing.compile.Compiler.javac

@Tag('integration')
class SeedSharedPrefixIsDeduplicatedSpec extends Specification {

    def 'two directives with shared prefix do not duplicate nodes'() {
        given:
        def source = JavaFileObjects.forSourceString('test.SharedPrefixMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface SharedPrefixMapper {
                @Map(target = "street", source = "input.address")
                @Map(target = "city", source = "input.address")
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
