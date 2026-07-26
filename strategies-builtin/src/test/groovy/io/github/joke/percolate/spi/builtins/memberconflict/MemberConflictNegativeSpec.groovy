package io.github.joke.percolate.spi.builtins.memberconflict

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.JavaFileObject

/**
 * Compile-testing coverage for {@code MemberPlan}'s class-member-agreement guard (design D11 of change
 * {@code decouple-engine-from-strategy-semantics}): two operations across one mapper's methods request one
 * {@code MemberRequest} dedup key with different {@code (fieldType, initializer)} pairs. {@link MemberConflictFixtureStrategy}
 * is a test-only strategy, registered on this module's test classpath only, that deliberately does not
 * discriminate its dedup key by the {@code @Map(format = "…")} value — the exact bug the guard exists to catch.
 */
@Tag('integration')
class MemberConflictNegativeSpec extends Specification {

    def 'two operations requesting one dedup key with different initializers fail the compile, naming the key and both operations'() {
        when:
        Compilation compilation = PercolateCompiler.compile(WIDGET, WIDGET_MAPPER)

        then:
        !compilation.errors().empty
        compilation.errors().any {
            final def message = it.getMessage(null)
            message.contains("conflicting member definitions for 'widget-member'")
                    && message.contains('left') && message.contains('right')
                    && message.contains('widget-from-left') && message.contains('widget-from-right')
        }
    }

    private static final JavaFileObject WIDGET = JavaFileObjects.forSourceLines(
            'examples.memberconflict.Widget',
            'package examples.memberconflict;',
            'public final class Widget {',
            '    private final String value;',
            '    private final String tag;',
            '    public Widget(String value, String tag) {',
            '        this.value = value;',
            '        this.tag = tag;',
            '    }',
            '    public String getValue() { return value; }',
            '    public String getTag() { return tag; }',
            '}')

    private static final JavaFileObject WIDGET_MAPPER = JavaFileObjects.forSourceLines(
            'examples.memberconflict.WidgetMapper',
            'package examples.memberconflict;',
            'import io.github.joke.percolate.Map;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface WidgetMapper {',
            '    @Map(target = "", source = "a", format = "left")',
            '    Widget mapLeft(String a);',
            '    @Map(target = "", source = "b", format = "right")',
            '    Widget mapRight(String b);',
            '}')
}
