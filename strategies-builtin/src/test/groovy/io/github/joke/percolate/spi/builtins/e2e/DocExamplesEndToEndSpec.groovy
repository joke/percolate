package io.github.joke.percolate.spi.builtins.e2e

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

/**
 * Compiles the doc-owned example fixtures the manual include::s and pins the generated shapes the prose
 * describes, so a published example cannot drift from compiled code.
 */
@Tag('integration')
class DocExamplesEndToEndSpec extends Specification {

    def 'quick-start PersonMapper generates a direct constructor call'() {
        when:
        def content = generate('getting-started/PersonMapper.java',
                'io.github.joke.percolate.docs.gettingstarted.PersonMapperImpl')

        then:
        content.contains('return new Human(person.getFirstName())')
    }

    def 'nested source and target paths chain accessors and nest construction'() {
        when:
        def content = generate('nested-paths/ProfileMapper.java',
                'io.github.joke.percolate.docs.nested.ProfileMapperImpl')

        then:
        content.contains('return new Profile(new Location(user.getCompany().getAddress().getCity()))')
    }

    def 'map-annotation AccountMapper combines source, constant, and defaultValue'() {
        when:
        def content = generate('map-annotation/AccountMapper.java',
                'io.github.joke.percolate.docs.mapannotation.AccountMapperImpl')

        then: 'the constant is supplied with no source'
        content.contains('"ACTIVE"')

        and: 'the defaultValue coalesces the absent-capable source'
        content.contains('requireNonNullElse(form.getDisplayName(), "unknown")')

        and: 'the result is assembled'
        content.contains('new Account(')
    }

    def 'collections TeamMapper composes a stream/map/collect pipeline delegating to the element method'() {
        when:
        def content = generate('collections/TeamMapper.java',
                'io.github.joke.percolate.docs.collections.TeamMapperImpl')

        then:
        content.contains(
                'team.getMembers().stream().map(member -> this.toView(member)).collect(Collectors.toList())')

        and: 'the element method is generated'
        content.contains('public MemberView toView(Member member)')
        content.contains('return new MemberView(member.getName())')
    }

    private static String generate(final String resource, final String implFqn) {
        Compilation c = PercolateCompiler.compile(JavaFileObjects.forResource(resource))
        assert c.errors().empty
        def gen = c.generatedSourceFile(implFqn)
        assert gen.present
        gen.get().getCharContent(true).toString()
    }
}
