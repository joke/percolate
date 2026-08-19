package io.github.joke.percolate.docs.setters

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.JavaFileObject

/**
 * Backs the setter-assembly page's ranking section and the compile-time-switches page's
 * {@code percolate.helpers.*} entries. Both options are about the <em>shape of what is emitted</em> rather than
 * about runtime behaviour, so they need the same fixture compiled more than once — which an ordinary
 * single-configuration compile cannot express. The real processor therefore runs through the
 * {@code compile-testing} harness once per setting, and each real generated file is materialised to
 * {@code build/generated-doc-examples/setters/} for the pages' {@code include::}s.
 */
@Tag('integration')
class SetterOptionDocExampleSpec extends Specification {

    private static final JavaFileObject PREFERENCE_MAPPER =
            JavaFileObjects.forResource('examples/setters/SetterPreferenceMapper.java')

    private static final JavaFileObject HELPER_STYLE_MAPPER =
            JavaFileObjects.forResource('examples/setters/HelperStyleMapper.java')

    def 'a target admitting all three forms assembles through its constructor by default'() {
        when:
        Compilation compilation = PercolateCompiler.compileWith(['-Apercolate.docTags=true'], PREFERENCE_MAPPER)

        then:
        compilation.errors().empty
        def content = sourceOf(compilation, 'examples.setters.SetterPreferenceMapperImpl')
        content.contains('return new Ticket(')
        !content.contains('Ticket.builder()')
        !content.contains('assembleTicket')

        and:
        materialise('preference-constructor/SetterPreferenceMapperImpl.java', content)
    }

    def 'the same target assembles through its setters when the preference ranks the setter form first'() {
        when:
        Compilation compilation = PercolateCompiler.compileWith(
                ['-Apercolate.docTags=true', '-Apercolate.construction.preference=setter'], PREFERENCE_MAPPER)

        then:
        compilation.errors().empty
        def content = sourceOf(compilation, 'examples.setters.SetterPreferenceMapperImpl')
        content.contains('assembleTicket')
        content.contains('result.setCode(')
        !content.contains('Ticket.builder()')

        and:
        materialise('preference-setter/SetterPreferenceMapperImpl.java', content)
    }

    def 'a multi-token list is honoured in order, so the second token wins when the first form does not match'() {
        when: 'the builder ranks first and the setter second, and Note has no builder'
        Compilation compilation = PercolateCompiler.compileWith(
                ['-Apercolate.construction.preference=builder,setter'], PREFERENCE_MAPPER)

        then:
        compilation.errors().empty
        def content = sourceOf(compilation, 'examples.setters.SetterPreferenceMapperImpl')

        expect: 'Ticket takes the builder it does have, Note falls to the setters ranked next'
        content.contains('Ticket.builder()')
        content.contains('assembleNote')
    }

    def 'the preference is a preference, never an exclusion: a setter-only target still assembles'() {
        when:
        Compilation compilation = PercolateCompiler.compileWith(
                ['-Apercolate.construction.preference=constructor'], PREFERENCE_MAPPER)

        then:
        compilation.errors().empty

        expect: 'Note has neither an all-args constructor nor a builder, so it assembles through its setters'
        sourceOf(compilation, 'examples.setters.SetterPreferenceMapperImpl').contains('assembleNote')
    }

    def 'the default helper style emits a private static helper'() {
        when:
        Compilation compilation = PercolateCompiler.compileWith(['-Apercolate.docTags=true'], HELPER_STYLE_MAPPER)

        then:
        compilation.errors().empty
        def content = sourceOf(compilation, 'examples.setters.HelperStyleMapperImpl')
        content.contains('private static Badge assembleBadge(')

        and:
        materialise('helpers-default/HelperStyleMapperImpl.java', content)
    }

    def 'percolate.helpers.visibility sets the helper access modifier'() {
        when:
        Compilation compilation = PercolateCompiler.compileWith(
                ['-Apercolate.docTags=true', '-Apercolate.helpers.visibility=protected'], HELPER_STYLE_MAPPER)

        then:
        compilation.errors().empty
        def content = sourceOf(compilation, 'examples.setters.HelperStyleMapperImpl')
        content.contains('protected static Badge assembleBadge(')

        and:
        materialise('helpers-protected/HelperStyleMapperImpl.java', content)
    }

    def 'percolate.helpers.visibility=package emits no access modifier at all'() {
        when:
        Compilation compilation = PercolateCompiler.compileWith(
                ['-Apercolate.helpers.visibility=package'], HELPER_STYLE_MAPPER)

        then:
        compilation.errors().empty

        expect:
        sourceOf(compilation, 'examples.setters.HelperStyleMapperImpl').contains('static Badge assembleBadge(')
    }

    def 'percolate.helpers.static=false drops static from the helper'() {
        when:
        Compilation compilation = PercolateCompiler.compileWith(
                ['-Apercolate.docTags=true', '-Apercolate.helpers.static=false'], HELPER_STYLE_MAPPER)

        then:
        compilation.errors().empty
        def content = sourceOf(compilation, 'examples.setters.HelperStyleMapperImpl')
        content.contains('private Badge assembleBadge(')

        and:
        materialise('helpers-instance/HelperStyleMapperImpl.java', content)
    }

    def 'the two helper options compose'() {
        when:
        Compilation compilation = PercolateCompiler.compileWith(
                ['-Apercolate.helpers.visibility=public', '-Apercolate.helpers.static=false'], HELPER_STYLE_MAPPER)

        then:
        compilation.errors().empty

        expect:
        sourceOf(compilation, 'examples.setters.HelperStyleMapperImpl').contains('public Badge assembleBadge(')
    }

    def 'an unrecognised helper visibility degrades to private rather than failing the round'() {
        when:
        Compilation compilation = PercolateCompiler.compileWith(
                ['-Apercolate.helpers.visibility=wombat'], HELPER_STYLE_MAPPER)

        then:
        compilation.errors().empty

        expect:
        sourceOf(compilation, 'examples.setters.HelperStyleMapperImpl').contains('private static Badge assembleBadge(')
    }

    private static String sourceOf(final Compilation compilation, final String qualifiedName) {
        def generated = compilation.generatedSourceFile(qualifiedName)
        assert generated.present
        generated.get().getCharContent(true).toString()
    }

    private static void materialise(final String relativePath, final String content) {
        def file = new File("build/generated-doc-examples/setters/${relativePath}")
        file.parentFile.mkdirs()
        file.text = content
    }
}
