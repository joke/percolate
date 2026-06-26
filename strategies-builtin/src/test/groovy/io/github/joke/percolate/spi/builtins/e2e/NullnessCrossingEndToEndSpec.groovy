package io.github.joke.percolate.spi.builtins.e2e

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

@Tag('integration')
class NullnessCrossingEndToEndSpec extends Specification {

    def 'a nullable source feeding a non-null target renders a requireNonNull crossing'() {
        given:
        def packageInfo = JavaFileObjects.forSourceLines('test.package-info',
                '@org.jspecify.annotations.NullMarked',
                'package test;')
        def source = JavaFileObjects.forSourceLines('test.Source',
                'package test;',
                'import org.jspecify.annotations.Nullable;',
                'public final class Source {',
                '    private final @Nullable String name;',
                '    public Source(@Nullable String name) { this.name = name; }',
                '    public @Nullable String getName() { return name; }',
                '}')
        def target = JavaFileObjects.forSourceLines('test.Target',
                'package test;',
                'public final class Target {',
                '    private final String name;',
                '    public Target(String name) { this.name = name; }',
                '    public String getName() { return name; }',
                '}')
        def mapper = JavaFileObjects.forSourceLines('test.M',
                'package test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '@Mapper',
                'public interface M {',
                '    @Map(target = "name", source = "src.name")',
                '    Target map(Source src);',
                '}')

        when:
        Compilation compilation = PercolateCompiler.compile(packageInfo, source, target, mapper)

        then: 'compilation succeeds and the generated body guards the nullable source'
        compilation.errors().empty
        def generated = compilation.generatedSourceFile('test.MImpl')
        generated.present
        def content = generated.get().getCharContent(true).toString()
        content.contains('requireNonNull')
        content.contains("source for slot 'name' is null but target is non-null")
    }
}
