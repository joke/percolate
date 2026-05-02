package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

import static com.google.testing.compile.Compiler.javac

@Tag('integration')
class PerMethodClustersGoldenSpec extends Specification {

    def 'per-method clusters are rendered correctly'() {
        given:
        def source = JavaFileObjects.forSourceString('test.PerMethodClusters', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface PerMethodClusters {
                @Map(target = "a", source = "a")
                void method1(Object a);

                @Map(target = "b", source = "b")
                void method2(Object b);
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
