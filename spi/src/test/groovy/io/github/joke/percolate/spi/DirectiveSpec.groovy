package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

/**
 * {@link Directive}'s by-key default methods over the open input bag. {@link BagDirective} implements only the two
 * abstract members ({@code sourcePath}/{@code inputs}), leaving {@link Directive#input}, {@link Directive#inputs} and
 * {@link Directive#value} as the interface's own default bodies, so each is exercised for real. Inputs are built
 * through the published {@link DirectiveInput} factories rather than mocked: they are values, and a strategy reading
 * its own member by key is exactly what this contract promises.
 */
@Tag('unit')
class DirectiveSpec extends Specification {

    Subject subject = Mock()

    def 'input returns the sole entry declared under the key'() {
        def format = DirectiveInput.scalar('format', 'yyyy-MM-dd', subject)
        def directive = new BagDirective([DirectiveInput.scalar('constant', '3', subject), format])

        when:
        def result = directive.input('format')

        then:
        0 * _

        expect:
        result.get().is(format)
    }

    def 'input is empty when no entry carries the key'() {
        def directive = new BagDirective([DirectiveInput.scalar('constant', '3', subject)])

        when:
        def result = directive.input('format')

        then:
        0 * _

        expect:
        !result.present
    }

    def 'input takes the first entry when the key repeats'() {
        def first = DirectiveInput.structured('enum', [source: 'A'], subject)
        def directive = new BagDirective([first, DirectiveInput.structured('enum', [source: 'B'], subject)])

        when:
        def result = directive.input('enum')

        then:
        0 * _

        expect:
        result.get().is(first)
    }

    def 'inputs returns every entry under the key, in declaration order'() {
        def first = DirectiveInput.structured('enum', [source: 'A'], subject)
        def second = DirectiveInput.structured('enum', [source: 'B'], subject)
        def directive = new BagDirective([first, DirectiveInput.scalar('format', 'x', subject), second])

        when:
        def result = directive.inputs('enum')

        then:
        0 * _

        expect:
        result.size() == 2
        result[0].is(first)
        result[1].is(second)
    }

    def 'inputs is empty when no entry carries the key'() {
        def directive = new BagDirective([DirectiveInput.scalar('format', 'x', subject)])

        when:
        def result = directive.inputs('enum')

        then:
        0 * _

        expect:
        result.empty
    }

    def 'value reads the scalar literal declared under the key'() {
        def directive = new BagDirective([DirectiveInput.scalar('constant', '3', subject)])

        when:
        def result = directive.value('constant')

        then:
        0 * _

        expect:
        result.get() == '3'
    }

    // An empty string is a written value, never an absent one — the distinction the whole bag exists to preserve.
    def 'value reports an empty literal as present'() {
        def directive = new BagDirective([DirectiveInput.scalar('constant', '', subject)])

        when:
        def result = directive.value('constant')

        then:
        0 * _

        expect:
        result.get() == ''
    }

    def 'value is empty when the key is absent'() {
        def directive = new BagDirective([DirectiveInput.scalar('format', 'x', subject)])

        when:
        def result = directive.value('constant')

        then:
        0 * _

        expect:
        !result.present
    }

    // A structured entry carries its parts through member(), so it has no scalar value to report.
    def 'value is empty when the key names a structured entry'() {
        def directive = new BagDirective([DirectiveInput.structured('enum', [source: 'A'], subject)])

        when:
        def result = directive.value('enum')

        then:
        0 * _

        expect:
        !result.present
    }

    /** Implements only the two abstract {@link Directive} members, leaving the by-key lookups as the real defaults. */
    private static class BagDirective implements Directive {

        private final List<DirectiveInput> bag

        BagDirective(final List<DirectiveInput> bag) {
            this.bag = bag
        }

        @Override
        List<String> sourcePath() {
            []
        }

        @Override
        List<DirectiveInput> inputs() {
            bag
        }
    }
}
