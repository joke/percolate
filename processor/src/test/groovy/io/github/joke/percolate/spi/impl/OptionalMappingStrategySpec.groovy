package io.github.joke.percolate.spi.impl

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class OptionalMappingStrategySpec extends Specification {

    def "wraps non-Optional source into Optional target via Optional.ofNullable"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.SourceMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface SourceMapper {',
            '    Result map(Source source);',
            '    TargetItem mapItem(SourceItem item);',
            '}',
        )
        def source = JavaFileObjects.forSourceLines('test.Source',
            'package test;',
            'public class Source {',
            '    public SourceItem getItem() { return null; }',
            '}',
        )
        def result = JavaFileObjects.forSourceLines('test.Result',
            'package test;',
            'import java.util.Optional;',
            'public class Result {',
            '    public final Optional<TargetItem> item;',
            '    public Result(Optional<TargetItem> item) {',
            '        this.item = item;',
            '    }',
            '}',
        )
        def sourceItem = JavaFileObjects.forSourceLines('test.SourceItem',
            'package test;',
            'public class SourceItem {',
            '    public String getValue() { return ""; }',
            '}',
        )
        def targetItem = JavaFileObjects.forSourceLines('test.TargetItem',
            'package test;',
            'public class TargetItem {',
            '    public final String value;',
            '    public TargetItem(String value) {',
            '        this.value = value;',
            '    }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, source, result, sourceItem, targetItem)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation)
            .generatedSourceFile('test.SourceMapperImpl')
            .contentsAsUtf8String()
            .contains('java.util.Optional.ofNullable(this.mapItem(source.getItem()))')
    }
}
