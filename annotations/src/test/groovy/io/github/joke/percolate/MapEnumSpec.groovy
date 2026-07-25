package io.github.joke.percolate

import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.MapEnumProbe
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class MapEnumSpec extends Specification {

    def '@MapEnum is repeatable on a method and each declaration is readable by the processor'() {
        def source = JavaFileObjects.forSourceLines('examples.EnumMethod',
                'package examples;',
                'import io.github.joke.percolate.MapEnum;',
                'interface EnumMethod {',
                '    @MapEnum(source = "NEW", target = "CREATED")',
                '    @MapEnum(source = "COMPLETED", target = "FULFILLED")',
                '    void convert();',
                '}')

        when:
        def compilation = Compiler.javac().withProcessors(new MapEnumProbe()).compile(source)

        then:
        compilation.errors().empty
        def messages = compilation.notes()*.getMessage(null)
        messages.contains('MapEnum:NEW->CREATED')
        messages.contains('MapEnum:COMPLETED->FULFILLED')
    }
}
