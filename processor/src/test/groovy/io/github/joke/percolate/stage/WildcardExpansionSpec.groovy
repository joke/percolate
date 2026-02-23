package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class WildcardExpansionSpec extends Specification {

    def "expands wildcard source and maps matching named properties"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.WildcardMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper',
            'public interface WildcardMapper {',
            '    @Map(target = ".", source = "order.*")',
            '    FlatOrder map(Order order);',
            '}',
        )
        def order = JavaFileObjects.forSourceLines('test.Order',
            'package test;',
            'public class Order {',
            '    public long getOrderId() { return 0; }',
            '    public String getStatus() { return ""; }',
            '}',
        )
        def flatOrder = JavaFileObjects.forSourceLines('test.FlatOrder',
            'package test;',
            'public class FlatOrder {',
            '    public final long orderId;',
            '    public final String status;',
            '    public FlatOrder(long orderId, String status) {',
            '        this.orderId = orderId;',
            '        this.status = status;',
            '    }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, order, flatOrder)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation)
            .generatedSourceFile('test.WildcardMapperImpl')
            .contentsAsUtf8String()
            .contains('order.getOrderId()')
        assertThat(compilation)
            .generatedSourceFile('test.WildcardMapperImpl')
            .contentsAsUtf8String()
            .contains('order.getStatus()')
    }
}
