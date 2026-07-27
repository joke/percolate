package io.github.joke.percolate.docs.extending

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.JavaFileObject

/**
 * Backs the manual's Extending (SPI) page section on {@code DirectiveReader}. {@link Literal} is a brand-new,
 * percolate-unaware annotation; {@link LiteralDirectiveReader} and {@link LiteralValue} — registered only on
 * this module's test classpath — are the whole extension needed to teach percolate its vocabulary. Compiled
 * through the real {@code compile-testing} harness rather than an ordinary {@code compileTestJava} run: a
 * reader must be discoverable via {@code ServiceLoader} on the same compilation that processes the annotation
 * it owns, which the module's own {@code compileTestJava} (annotation-processed by the published
 * {@code percolate} starter, not this module's own test classes) cannot provide.
 */
@Tag('integration')
class LiteralDirectiveReaderDocExampleSpec extends Specification {

    private static final JavaFileObject GREETING = forResource('examples/extending/Greeting.java')
    private static final JavaFileObject LITERAL_MAPPER = forResource('examples/extending/LiteralMapper.java')

    def 'a @Literal-annotated method generates a plain literal assignment with no source read'() {
        when:
        Compilation compilation =
                PercolateCompiler.compileWith(['-Apercolate.docTags=true'], GREETING, LITERAL_MAPPER)

        then:
        compilation.errors().empty
        def content = sourceOf(compilation, 'examples.extending.LiteralMapperImpl')
        content.contains('"hello"')

        and:
        materialise('LiteralMapperImpl.java', content)
    }

    private static String sourceOf(final Compilation compilation, final String qualifiedName) {
        compilation.generatedSourceFile(qualifiedName).get().getCharContent(true).toString()
    }

    private static JavaFileObject forResource(final String path) {
        JavaFileObjects.forResource(path)
    }

    private static void materialise(final String relativePath, final String content) {
        def file = new File("build/generated-doc-examples/extending/${relativePath}")
        file.parentFile.mkdirs()
        file.text = content
    }
}
