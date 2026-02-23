package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class MultiParamCodeGenSpec extends Specification {

    def "generates impl for multi-param mapper with explicit @Map directives"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.MergeMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper',
            'public interface MergeMapper {',
            '    @Map(target = "id", source = "a.id")',
            '    @Map(target = "label", source = "b.name")',
            '    Merged merge(TypeA a, TypeB b);',
            '}',
        )
        def typeA = JavaFileObjects.forSourceLines('test.TypeA',
            'package test;',
            'public class TypeA {',
            '    public long getId() { return 0; }',
            '}',
        )
        def typeB = JavaFileObjects.forSourceLines('test.TypeB',
            'package test;',
            'public class TypeB {',
            '    public String getName() { return ""; }',
            '}',
        )
        def merged = JavaFileObjects.forSourceLines('test.Merged',
            'package test;',
            'public class Merged {',
            '    public final long id;',
            '    public final String label;',
            '    public Merged(long id, String label) {',
            '        this.id = id;',
            '        this.label = label;',
            '    }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, typeA, typeB, merged)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation)
            .generatedSourceFile('test.MergeMapperImpl')
            .contentsAsUtf8String()
            .contains('a.getId()')
        assertThat(compilation)
            .generatedSourceFile('test.MergeMapperImpl')
            .contentsAsUtf8String()
            .contains('b.getName()')
    }
}
