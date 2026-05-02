package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class DotEscapingGoldenSpec extends Specification {

    def 'escapeDot handles backslash'() {
        expect:
        DotRenderer.escapeDot('a\\b') == 'a\\\\b'
    }

    def 'escapeDot handles double quote'() {
        expect:
        DotRenderer.escapeDot('a"b') == 'a\\"b'
    }

    def 'escapeDot handles newline'() {
        expect:
        DotRenderer.escapeDot('a\nb') == 'a\\nb'
    }

    def 'escapeDot handles angle brackets'() {
        expect:
        DotRenderer.escapeDot('<b>') == '\\<b\\>'
    }

    def 'escapeDot handles multiple special characters'() {
        expect:
        DotRenderer.escapeDot('"hello" <world> \\path') == '\\"hello\\" \\<world\\> \\\\path'
    }

    def 'escapeDot leaves safe characters unchanged'() {
        expect:
        DotRenderer.escapeDot('hello') == 'hello'
        DotRenderer.escapeDot('map(Person)') == 'map(Person)'
        DotRenderer.escapeDot('src[name]') == 'src[name]'
    }
}
