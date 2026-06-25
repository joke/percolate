package io.github.joke.percolate.processor.stages.expand

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification
import spock.lang.Tag

/**
 * When one source-path segment is realisable both as a JavaBean getter and as a visible field, forward descent
 * over-emits both accessors into the same source {@code Value} and plan extraction prunes by weight — so the cheaper
 * getter (STEP_GETTER = 1) wins over the field (STEP_FIELD = 3), deterministically and independently of the
 * {@code ServiceLoader} registration order. This is the behaviour the old {@code findFirst}-over-the-unsorted-list
 * accessor selection could not guarantee.
 */
@Tag('integration')
class SourcePathAccessorAmbiguitySpec extends Specification {

    def 'a segment realisable as both a getter and a public field prefers the getter by cost'() {
        given: 'Src exposes name BOTH as a public field and a getName() getter'
        def src = JavaFileObjects.forSourceLines('test.Src',
                'package test;',
                'public final class Src {',
                '    public final String name;',
                '    public Src(String name) { this.name = name; }',
                '    public String getName() { return name; }',
                '}')
        def target = JavaFileObjects.forSourceLines('test.Dst',
                'package test;',
                'public final class Dst {',
                '    private final String name;',
                '    public Dst(String name) { this.name = name; }',
                '    public String getName() { return name; }',
                '}')
        def mapper = JavaFileObjects.forSourceLines('test.M',
                'package test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '@Mapper',
                'public interface M {',
                '    @Map(target = "name", source = "in.name")',
                '    Dst map(Src in);',
                '}')

        when:
        Compilation compilation = Compiler.javac()
                .withProcessors(new PercolateProcessor())
                .compile(src, target, mapper)

        then: 'the getter is chosen, never the bare field read'
        compilation.errors().empty
        def content = compilation.generatedSourceFile('test.MImpl').get().getCharContent(true).toString()
        content.contains('in.getName()')
        !content.contains('in.name')
    }
}
