package io.github.joke.percolate.reactor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's reactive page. The doc-owned {@code FeedMapper} fixture — a top-level
 * {@code Flux<Article> -> Flux<ArticleView>} mapper with a declared element method — compiles in the reactor
 * module and generates a non-blocking {@code flux.map} over the element transform, proving the documented
 * reactive example stays reactive end to end. The manual include::s the very same fixture.
 */
@Tag('integration')
class ReactiveDocExampleSpec extends Specification {

    def 'the reactive feed example composes flux.map over the element transform, never blocking'() {
        when:
        Compilation c = PercolateCompiler.compile(JavaFileObjects.forResource('examples/reactive/FeedMapper.java'))

        then: 'generation succeeds'
        c.errors().empty
        def gen = c.generatedSourceFile('io.github.joke.percolate.docs.reactive.FeedMapperImpl')
        gen.present
        def body = gen.get().getCharContent(true).toString()

        and: 'the Flux maps element-wise through the element method — no Stream, no blocking'
        body.contains('.map(')
        body.contains('toView(')
        !body.contains('.stream()')
        !body.contains('.block(')
    }
}
