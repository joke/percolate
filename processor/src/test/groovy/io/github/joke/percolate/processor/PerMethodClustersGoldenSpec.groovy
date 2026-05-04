package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic.Kind

@Tag('integration')
class PerMethodClustersGoldenSpec extends Specification {

    def 'per-method clusters are rendered correctly'() {
        given:
        def source = JavaFileObjects.forSourceString('test.PerMethodClusters', '''
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            class Input {
                String getA() { return "a"; }
                String getB() { return "b"; }
            }

            class Output {
                final String a;
                final String b;
                Output(String a, String b) {
                    this.a = a;
                    this.b = b;
                }
            }

            @Mapper
            public interface PerMethodClusters {
                @Map(target = "a", source = "a.a")
                @Map(target = "b", source = "a.b")
                Output method1(Input a);

                @Map(target = "a", source = "b.a")
                @Map(target = "b", source = "b.b")
                Output method2(Input b);
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
