package io.github.joke.percolate.processor.internal.stages.generate

import com.google.testing.compile.Compilation
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

/**
 * The opt-in {@code -Apercolate.docTags} option brackets each generated method body with AsciiDoc include-tag
 * comments named after the method, so a documentation build can single-source the generated body by tag. Off by
 * default, so ordinary consumer output carries no tags. Engine-only: driven by the registered {@code FakeStrategy}.
 */
@Tag('integration')
class DocTagsEmissionSpec extends Specification {

    def 'doc tags bracket the generated method body only when the option is on'() {
        given:
        def src = PercolateCompiler.source('test.Src', 'package test;', 'public final class Src {}')
        def mapper = PercolateCompiler.source('test.M',
                'package test;',
                'import io.github.joke.percolate.Mapper;',
                '@Mapper',
                'public interface M {',
                '    String map(Src src);',
                '}')

        when: 'compiled with the docTags option on'
        Compilation on = PercolateCompiler.compileWith(['-Apercolate.docTags=true'], src, mapper)

        then: 'the generated body is bracketed by tag comments named after the method'
        on.errors().empty
        def onSrc = generated(on)
        onSrc.contains('// tag::map[]')
        onSrc.contains('// end::map[]')

        when: 'compiled without the option (the default)'
        Compilation off = PercolateCompiler.compile(src, mapper)

        then: 'the default output carries no documentation tags'
        off.errors().empty
        def offSrc = generated(off)
        !offSrc.contains('tag::')
        !offSrc.contains('end::')
    }

    private static String generated(final Compilation compilation) {
        def gen = compilation.generatedSourceFile('test.MImpl')
        assert gen.present
        gen.get().getCharContent(true).toString()
    }
}
