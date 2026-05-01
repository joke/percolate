package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

import static com.google.testing.compile.CompilationSubject.assertThat
import static com.google.testing.compile.Compiler.javac

@Tag('integration')
class DuplicateTargetIntegrationSpec extends Specification {

    def 'duplicate target produces error with location pointing at target literal'() {
        given:
        def source = JavaFileObjects.forSourceString('test.DuplicateTargetMapper', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface DuplicateTargetMapper {
                @Map(target = "name", source = "a")
                @Map(target = "name", source = "b")
                void map(Object input);
            }
        ''')

        when:
        Compilation compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source)

        then:
        compilation.status() == Compilation.Status.FAILURE
        def diagnostics = compilation.diagnostics()
        def errors = diagnostics.findAll { it.kind == Kind.ERROR }
        errors.size() == 1
        errors[0].getMessage(null).contains('name')
    }
}
