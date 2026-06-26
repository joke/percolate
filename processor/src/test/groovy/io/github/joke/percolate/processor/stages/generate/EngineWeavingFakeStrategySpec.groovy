package io.github.joke.percolate.processor.stages.generate

import com.google.testing.compile.Compilation
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

/**
 * The engine is strategy-agnostic. With NO real strategy on the classpath — only the {@code FakeStrategy} registered
 * via {@code META-INF/services} — the engine still self-seeds, selects the fake's zero-port producer and weaves its
 * sentinel into the return position, yielding a compiling mapper. This proves the engine depends on the SPI, never on
 * the builtins; the real conversion output is owned by the builtin e2e tests in {@code strategies-builtin}.
 */
@Tag('integration')
class EngineWeavingFakeStrategySpec extends Specification {

    def 'the engine weaves a fake-produced sentinel into the return position'() {
        given:
        def source = PercolateCompiler.source('test.Src',
                'package test;',
                'public final class Src {}')
        def mapper = PercolateCompiler.source('test.FakeMapper',
                'package test;',
                'import io.github.joke.percolate.Mapper;',
                '@Mapper',
                'public interface FakeMapper {',
                '    String map(Src src);',
                '}')

        when:
        Compilation compilation = PercolateCompiler.compile(source, mapper)

        then: 'generation succeeded with only the fake strategy present'
        compilation.errors().empty

        and: 'the fake sentinel ((String) null) was woven into the return'
        def impl = compilation.generatedSourceFile('test.FakeMapperImpl')
        impl.present
        impl.get().getCharContent(true).toString().contains('return (String) null')
    }
}
