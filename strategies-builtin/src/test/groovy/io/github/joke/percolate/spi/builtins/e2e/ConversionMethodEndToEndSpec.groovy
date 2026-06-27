package io.github.joke.percolate.spi.builtins.e2e

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's conversion-methods page. The fixture is the very file the page include::s
 * (strategies-builtin/src/test/resources/examples/conversion-method/CustomerMapper.java), so the documented example
 * cannot diverge from compiled, generated code.
 */
@Tag('integration')
class ConversionMethodEndToEndSpec extends Specification {

    def 'a second mapper method is reused as the conversion for a nested field'() {
        given: 'the example the manual includes'
        def mapper = JavaFileObjects.forResource('examples/conversion-method/CustomerMapper.java')

        when:
        Compilation compilation = PercolateCompiler.compile(mapper)

        then: 'generation succeeds'
        compilation.errors().empty
        def generated = compilation
                .generatedSourceFile('io.github.joke.percolate.docs.conversion.CustomerMapperImpl')
        generated.present

        and: 'map() calls the conversion method for the nested field rather than inlining the assembly'
        def content = generated.get().getCharContent(true).toString()
        content.contains('this.toView(customer.getAddress())')

        and: 'the conversion method is itself generated'
        content.contains('public AddressView toView(Address address)')
        content.contains('return new AddressView(address.getStreet())')
    }
}
