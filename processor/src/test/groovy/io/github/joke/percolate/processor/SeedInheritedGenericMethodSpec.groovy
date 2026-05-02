package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

import static com.google.testing.compile.Compiler.javac

@Tag('integration')
class SeedInheritedGenericMethodSpec extends Specification {

    def 'inherited generic method is seeded the same as a directly declared method'() {
        given:
        def source = JavaFileObjects.forSourceString('test.InheritedGenericMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface InheritedGenericMapper extends BaseMapper {
            }

            interface BaseMapper {
                @Map(target = "name", source = "value")
                Object map(Object value);
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
