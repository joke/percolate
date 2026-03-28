package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import static com.google.testing.compile.Compiler.javac

@Tag('integration')
class PercolateProcessorSpec extends Specification {

    def 'processes @Mapper annotated interface without errors'() {
        given:
        def source = JavaFileObjects.forSourceString('test.TestMapper', '''
            import io.github.joke.percolate.Mapper;

            @Mapper
            public interface TestMapper {
            }
        ''')

        when:
        Compilation compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source)

        then:
        compilation.status() == Compilation.Status.SUCCESS
    }
}
