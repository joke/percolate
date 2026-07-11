package io.github.joke.percolate.docs.switches

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.JavaFileObject

/**
 * Backs the manual's Compile-time-switches reference, co-located here because every option is a
 * {@code processor}-owned feature (design D4). Unlike the other doc-e2e (which assert runtime behaviour and
 * materialise real {@code compileTestJava} output), these options are purely about *generated-text shape* — so
 * this spec runs the real {@link io.github.joke.percolate.processor.PercolateProcessor} through the real
 * {@code compile-testing} harness once per option combination (needed to compare a switch on vs off, which an
 * ordinary single-configuration {@code compileTestJava} run cannot express), asserts the rendered difference
 * directly, and materialises each real generated file to {@code build/generated-doc-examples/switches/} for the
 * page's {@code include::}s — the same "spec is the sole materialiser" discipline the original real-compile doc
 * pattern used (see {@code openspec/changes/archive/2026-06-27-single-source-manual-examples/design.md}, D2).
 */
@Tag('integration')
class CompileTimeSwitchesDocExampleSpec extends Specification {

    private static final JavaFileObject PRODUCT_MAPPER = forResource('examples/switches/ProductMapper.java')
    private static final JavaFileObject NULLABLE_MAPPER = forResource('examples/switches/NullableMapper.java')
    private static final JavaFileObject CUSTOM_NULLABLE = forResource('examples/switches/CustomNullable.java')
    private static final JavaFileObject ZONED_MAPPER = forResource('examples/switches/ZonedMapper.java')

    def 'percolate.docTags wraps each generated method body in include-tags, off by default'() {
        when:
        Compilation off = PercolateCompiler.compile(PRODUCT_MAPPER)
        Compilation on = PercolateCompiler.compileWith(['-Apercolate.docTags=true'], PRODUCT_MAPPER)

        then:
        off.errors().empty
        on.errors().empty
        def offContent = sourceOf(off, 'examples.switches.ProductMapperImpl')
        def onContent = sourceOf(on, 'examples.switches.ProductMapperImpl')
        !offContent.contains('tag::map[]')
        onContent.contains('tag::map[]')
        onContent.contains('end::map[]')

        and:
        materialise('doctags-off/ProductMapperImpl.java', offContent)
        materialise('doctags-on/ProductMapperImpl.java', onContent)
    }

    def 'percolate.locals.final prefixes every hoisted local declaration with final'() {
        when:
        Compilation compilation = PercolateCompiler.compileWith(
                ['-Apercolate.docTags=true', '-Apercolate.locals.final=true'], PRODUCT_MAPPER)

        then:
        compilation.errors().empty
        def content = sourceOf(compilation, 'examples.switches.ProductMapperImpl')
        content.contains('final String name = product.getName();')
        content.contains('final int price = product.getPrice();')

        and:
        materialise('locals-final/ProductMapperImpl.java', content)
    }

    def 'percolate.locals.var renders every hoisted local as var instead of its explicit type'() {
        when:
        Compilation compilation = PercolateCompiler.compileWith(
                ['-Apercolate.docTags=true', '-Apercolate.locals.var=true'], PRODUCT_MAPPER)

        then:
        compilation.errors().empty
        def content = sourceOf(compilation, 'examples.switches.ProductMapperImpl')
        content.contains('var name = product.getName();')
        content.contains('var price = product.getPrice();')

        and:
        materialise('locals-var/ProductMapperImpl.java', content)
    }

    def 'percolate.classes.final declares the generated class final, off by default'() {
        when:
        Compilation off = PercolateCompiler.compileWith(['-Apercolate.docTags=true'], PRODUCT_MAPPER)
        Compilation on = PercolateCompiler.compileWith(
                ['-Apercolate.docTags=true', '-Apercolate.classes.final=true'], PRODUCT_MAPPER)

        then:
        off.errors().empty
        on.errors().empty
        def offContent = sourceOf(off, 'examples.switches.ProductMapperImpl')
        def onContent = sourceOf(on, 'examples.switches.ProductMapperImpl')
        offContent.contains('public class ProductMapperImpl')
        !offContent.contains('public final class ProductMapperImpl')
        onContent.contains('public final class ProductMapperImpl')

        and:
        materialise('classes-final-off/ProductMapperImpl.java', offContent)
        materialise('classes-final-on/ProductMapperImpl.java', onContent)
    }

    def 'percolate.methods.final declares each generated method final, off by default'() {
        when:
        Compilation off = PercolateCompiler.compileWith(['-Apercolate.docTags=true'], PRODUCT_MAPPER)
        Compilation on = PercolateCompiler.compileWith(
                ['-Apercolate.docTags=true', '-Apercolate.methods.final=true'], PRODUCT_MAPPER)

        then:
        off.errors().empty
        on.errors().empty
        def offContent = sourceOf(off, 'examples.switches.ProductMapperImpl')
        def onContent = sourceOf(on, 'examples.switches.ProductMapperImpl')
        !offContent.contains('public final ProductView map(')
        onContent.contains('public final ProductView map(')

        and:
        materialise('methods-final-off/ProductMapperImpl.java', offContent)
        materialise('methods-final-on/ProductMapperImpl.java', onContent)
    }

    def 'percolate.parameters.final declares each generated method parameter final, off by default'() {
        when:
        Compilation off = PercolateCompiler.compileWith(['-Apercolate.docTags=true'], PRODUCT_MAPPER)
        Compilation on = PercolateCompiler.compileWith(
                ['-Apercolate.docTags=true', '-Apercolate.parameters.final=true'], PRODUCT_MAPPER)

        then:
        off.errors().empty
        on.errors().empty
        def offContent = sourceOf(off, 'examples.switches.ProductMapperImpl')
        def onContent = sourceOf(on, 'examples.switches.ProductMapperImpl')
        !offContent.contains('map(final Product')
        onContent.contains('map(final Product')

        and:
        materialise('parameters-final-off/ProductMapperImpl.java', offContent)
        materialise('parameters-final-on/ProductMapperImpl.java', onContent)
    }

    def 'percolate.nullable.annotations registers a third-party @Nullable as a nullness marker'() {
        when:
        Compilation unregistered = PercolateCompiler.compileWith(
                ['-Apercolate.docTags=true'], NULLABLE_MAPPER, CUSTOM_NULLABLE)
        Compilation registered = PercolateCompiler.compileWith(
                ['-Apercolate.docTags=true', '-Apercolate.nullable.annotations=examples.switches.CustomNullable'],
                NULLABLE_MAPPER, CUSTOM_NULLABLE)

        then:
        unregistered.errors().empty
        registered.errors().empty
        def unregisteredContent = sourceOf(unregistered, 'examples.switches.NullableMapperImpl')
        def registeredContent = sourceOf(registered, 'examples.switches.NullableMapperImpl')
        !unregisteredContent.contains('requireNonNull')
        registeredContent.contains('requireNonNull')
        registeredContent.contains("source for slot 'trackingCode' is null but target is non-null")

        and:
        materialise('nullable-unregistered/NullableMapperImpl.java', unregisteredContent)
        materialise('nullable-registered/NullableMapperImpl.java', registeredContent)
    }

    def 'percolate.debug.graphs writes one GraphViz .dot file per method scope'() {
        when:
        Compilation off = PercolateCompiler.compile(PRODUCT_MAPPER)
        Compilation on = PercolateCompiler.compileWith(['-Apercolate.debug.graphs=true'], PRODUCT_MAPPER)

        then:
        off.errors().empty
        on.errors().empty
        !anyDotFile(off).present
        def dot = anyDotFile(on)
        dot.present
        def content = dot.get().getCharContent(true).toString()
        content.contains('digraph')

        and:
        materialise('debug-graphs/excerpt.dot', content.readLines().take(12).join('\n'))
    }

    def 'percolate.time.zone freezes the zone bridge to a project-wide default; unset defers to systemDefault()'() {
        when:
        Compilation unset = PercolateCompiler.compileWith(['-Apercolate.docTags=true'], ZONED_MAPPER)
        Compilation set = PercolateCompiler.compileWith(
                ['-Apercolate.docTags=true', '-Apercolate.time.zone=Europe/Berlin'], ZONED_MAPPER)

        then:
        unset.errors().empty
        set.errors().empty
        def unsetContent = sourceOf(unset, 'examples.switches.ZonedMapperImpl')
        def setContent = sourceOf(set, 'examples.switches.ZonedMapperImpl')
        unsetContent.contains('ZoneId.systemDefault()')
        !unsetContent.contains('Europe/Berlin')
        setContent.contains('ZoneId.of("Europe/Berlin")')

        and:
        materialise('time-zone-unset/ZonedMapperImpl.java', unsetContent)
        materialise('time-zone-set/ZonedMapperImpl.java', setContent)
    }

    private static Optional<JavaFileObject> anyDotFile(final Compilation compilation) {
        compilation.generatedFiles().stream()
                .filter(file -> file.name.endsWith('.dot'))
                .findFirst()
    }

    private static String sourceOf(final Compilation compilation, final String qualifiedName) {
        def generated = compilation.generatedSourceFile(qualifiedName)
        assert generated.present
        generated.get().getCharContent(true).toString()
    }

    private static JavaFileObject forResource(final String path) {
        JavaFileObjects.forResource(path)
    }

    private static void materialise(final String relativePath, final String content) {
        def file = new File("build/generated-doc-examples/switches/${relativePath}")
        file.parentFile.mkdirs()
        file.text = content
    }
}
