package io.github.joke.percolate.docs.builders

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.JavaFileObject

/**
 * Backs the builder page's {@code percolate.construction.preference} section. Unlike
 * {@link BuilderDocExampleSpec} — which asserts runtime behaviour from ordinary {@code compileTestJava} output —
 * this option is purely about which assembly form is <em>emitted</em>, so it needs the same fixture compiled more
 * than once. That is what an ordinary single-configuration compile cannot express, so the real processor runs
 * through the {@code compile-testing} harness once per setting, and each real generated file is materialised to
 * {@code build/generated-doc-examples/builders/} for the page's {@code include::}s.
 */
@Tag('integration')
class ConstructionPreferenceDocExampleSpec extends Specification {

    private static final JavaFileObject PREFERENCE_MAPPER = JavaFileObjects.forResource('examples/builders/PreferenceMapper.java')

    private static final JavaFileObject EMPTY_DECLARATION_MAPPER = JavaFileObjects.forResource('examples/builders/EmptyDeclarationMapper.java')

    def 'a target admitting both forms assembles through its constructor by default'() {
        when:
        Compilation compilation = PercolateCompiler.compileWith(['-Apercolate.docTags=true'], PREFERENCE_MAPPER)

        then:
        compilation.errors().empty
        def content = sourceOf(compilation, 'examples.builders.PreferenceMapperImpl')
        content.contains('return new Coupon(')
        !content.contains('Coupon.builder()')

        and:
        materialise('preference-constructor/PreferenceMapperImpl.java', content)
    }

    def 'the same target assembles through its builder when the preference names the builder'() {
        when:
        Compilation compilation = PercolateCompiler.compileWith(
                ['-Apercolate.docTags=true', '-Apercolate.construction.preference=builder'], PREFERENCE_MAPPER)

        then:
        compilation.errors().empty
        def content = sourceOf(compilation, 'examples.builders.PreferenceMapperImpl')
        content.contains('Coupon.builder()')
        content.contains('.code(')
        content.contains('.build()')

        and:
        materialise('preference-builder/PreferenceMapperImpl.java', content)
    }

    def 'the preference is a preference, never an exclusion: a builder-less target still assembles'() {
        when:
        Compilation compilation = PercolateCompiler.compileWith(
                ['-Apercolate.construction.preference=builder'], PREFERENCE_MAPPER)

        then:
        compilation.errors().empty

        expect: 'Voucher has no builder at all, so it assembles through its constructor regardless'
        sourceOf(compilation, 'examples.builders.PreferenceMapperImpl').contains('new Voucher(')
    }

    def 'an empty declaration is never vacuously assembled, by the builder or the constructor'() {
        when: 'the mapper declares no @Map at all, and the preference names the builder'
        Compilation compilation = PercolateCompiler.compileWith(
                ['-Apercolate.construction.preference=builder'], EMPTY_DECLARATION_MAPPER)

        then: 'the demand has no producer at all, so the round fails rather than emitting an assembly'
        !compilation.errors().empty

        expect: 'the failure IS the proof — had either form satisfied the empty declaration, this would compile'
        compilation.status() == Compilation.Status.FAILURE
    }

    private static String sourceOf(final Compilation compilation, final String qualifiedName) {
        def generated = compilation.generatedSourceFile(qualifiedName)
        assert generated.present
        generated.get().getCharContent(true).toString()
    }

    private static void materialise(final String relativePath, final String content) {
        def file = new File("build/generated-doc-examples/builders/${relativePath}")
        file.parentFile.mkdirs()
        file.text = content
    }
}
