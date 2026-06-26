package io.github.joke.percolate.spi.builtins.e2e

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's default-method-conversions page. The fixture is the very file the page
 * include::s (docs/modules/ROOT/examples/default-method/ProductMapper.java).
 */
@Tag('integration')
class DefaultMethodConversionEndToEndSpec extends Specification {

    def 'a default method supplies a conversion percolate calls'() {
        given: 'the example the manual includes'
        def mapper = JavaFileObjects.forResource('default-method/ProductMapper.java')

        when:
        Compilation compilation = PercolateCompiler.compile(mapper)

        then: 'generation succeeds'
        compilation.errors().empty
        def generated = compilation
                .generatedSourceFile('io.github.joke.percolate.docs.defaultmethod.ProductMapperImpl')
        generated.present

        and: 'map() calls the default method to produce the converted field'
        def content = generated.get().getCharContent(true).toString()
        content.contains('this.formatPrice(product.getPriceCents())')
    }
}
