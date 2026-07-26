package io.github.joke.percolate.spi.builtins.deferral

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.JavaFileObject

/**
 * Compile-testing coverage for design D14's deferral rule (change {@code decouple-engine-from-strategy-semantics}):
 * a mapper whose only recorded diagnostic is transient defers silently and realises once its producer becomes
 * available on a later round — no diagnostic is ever emitted along the way. {@link WidgetGeneratingProcessor} is a
 * test-only co-processor standing in for Lombok: it writes {@code examples.deferral.Widget} on its first round, a
 * brand-new source invisible to every processor (including {@code PercolateProcessor}) until the next round.
 * {@link DeferredWidgetFixtureStrategy} only offers a production once {@code Widget} is resolvable, so the mapper is
 * unrealisable in round 1 (recorded transient, not emitted) and realisable from round 2 onward.
 */
@Tag('integration')
class DeferredRealisationNegativeSpec extends Specification {

    def 'a mapper unrealisable only until a co-processor generates its producer\'s type realises with zero diagnostics'() {
        when:
        Compilation compilation = Compiler.javac()
                .withProcessors(new PercolateProcessor(), new WidgetGeneratingProcessor())
                .compile(WIDGET_MAPPER)

        then:
        compilation.errors().empty
        compilation.warnings().empty
        compilation.generatedSourceFile('examples.deferral.WidgetMapperImpl').present
    }

    private static final JavaFileObject WIDGET_MAPPER = JavaFileObjects.forSourceLines(
            'examples.deferral.WidgetMapper',
            'package examples.deferral;',
            'import io.github.joke.percolate.Map;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface WidgetMapper {',
            '    @Map(target = "", source = "seed")',
            '    Widget map(String seed);',
            '}')
}
