package io.github.joke.percolate.spi

import io.github.joke.percolate.javapoet.CodeBlock
import spock.lang.Specification
import spock.lang.Tag

/**
 * {@link DocTags#wrap} is the pure tag-wrapping transform relocated out of the engine's {@code BuildMethodBodies}:
 * it brackets a region between {@code // tag::<name>[]} / {@code // end::<name>[]} comment lines, interpolating the
 * name into both markers and reproducing the region verbatim. Asserted on the exact rendered output so the ordering,
 * both markers, the name interpolation, and the untouched region are all pinned (mutation-proof).
 */
@Tag('unit')
class DocTagsSpec extends Specification {

    def 'wraps a region between tag and end markers named after the tag'() {
        given:
        def region = CodeBlock.of('return x;\n')

        expect:
        DocTags.wrap(region, 'map').toString() == '// tag::map[]\nreturn x;\n// end::map[]\n'
    }

    def 'interpolates the tag name into both markers'() {
        expect:
        DocTags.wrap(CodeBlock.of('return x;\n'), name).toString() ==
                "// tag::${name}[]\nreturn x;\n// end::${name}[]\n"

        where:
        name << ['map', 'convert', 'toView']
    }

    def 'reproduces a multi-line region verbatim between the markers'() {
        given:
        def region = CodeBlock.builder()
                .addStatement('final var a = src.getA()')
                .addStatement('return new Target(a)')
                .build()

        when:
        def wrapped = DocTags.wrap(region, 'build').toString()

        then: 'the region body is unchanged, framed by the named markers in order'
        wrapped == '// tag::build[]\n' + region + '// end::build[]\n'

        and: 'the start marker precedes the region which precedes the end marker'
        wrapped.indexOf('// tag::build[]') < wrapped.indexOf('final var a') &&
                wrapped.indexOf('final var a') < wrapped.indexOf('// end::build[]')
    }

    def 'an empty region leaves the two markers adjacent'() {
        expect:
        DocTags.wrap(CodeBlock.of(''), 'empty').toString() == '// tag::empty[]\n// end::empty[]\n'
    }
}
