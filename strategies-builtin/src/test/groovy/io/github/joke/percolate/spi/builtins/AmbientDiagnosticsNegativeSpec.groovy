package io.github.joke.percolate.spi.builtins

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.PendingFeature
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.JavaFileObject

/**
 * Compile-testing coverage for the {@code @Ambient} diagnostics, now reported in port vocabulary by the engine's
 * own {@code REQUIRE}-miss handling (design D5 of change {@code decouple-engine-from-strategy-semantics}) rather
 * than {@code ValidateAmbientBindingsStage}, which this change deletes: an unbound binding name reachable from a
 * mapper method that publishes none, and a same-name type mismatch. Each must fail the compile with a message
 * naming the binding — never silently, and never only via the generic "no plan" diagnostic. The duplicate-key
 * check has no replacement yet — it is re-homed into {@code AmbientDirectiveReader} later in this same change
 * (group C1); see the {@code @PendingFeature} below.
 */
@Tag('integration')
class AmbientDiagnosticsNegativeSpec extends Specification {

    @PendingFeature(reason = 'duplicate-key detection moves to AmbientDirectiveReader in a later group of this change')
    def 'two @Ambient parameters of one method resolving to the same key fail the compile, naming the key'() {
        when:
        Compilation compilation = PercolateCompiler.compile(PERSON, ORDER, CUSTOMER, RESULT, DUPLICATE_KEY_MAPPER)

        then:
        !compilation.errors().empty
        compilation.errors().any { it.getMessage(null).contains("duplicate @Ambient key 'ctx'") }
    }

    def 'an ambient binding name unbound at every reachable mapper method fails the compile, naming it'() {
        when:
        Compilation compilation = PercolateCompiler.compile(PRICE, ORDER, RESULT_VIEW, UNBOUND_KEY_MAPPER)

        then:
        !compilation.errors().empty
        compilation.errors().any { it.getMessage(null).contains("scope input 'order', which no enclosing scope publishes") }
        !compilation.errors().any { it.getMessage(null).contains('no plan for') }
    }

    def 'a same-name type mismatch fails the compile, naming the binding and both types'() {
        when:
        Compilation compilation = PercolateCompiler.compile(
                CUSTOMER, CUSTOMER_ADDRESS, ORDER, ORDER_VIEW, ADDRESS_VIEW, MISMATCH_MAPPER)

        then:
        !compilation.errors().empty
        compilation.errors().any {
            final def msg = it.getMessage(null)
            msg.contains("scope input 'order'") && msg.contains('Order') && msg.contains('Customer')
        }
    }

    // ---- duplicate-key fixtures ------------------------------------------------------------------------------

    private static final JavaFileObject PERSON = JavaFileObjects.forSourceLines(
            'examples.ambientnegative.Person',
            'package examples.ambientnegative;',
            'public class Person {',
            '    private final String name;',
            '    public Person(String name) { this.name = name; }',
            '    public String getName() { return name; }',
            '}')

    private static final JavaFileObject ORDER = JavaFileObjects.forSourceLines(
            'examples.ambientnegative.Order',
            'package examples.ambientnegative;',
            'public class Order {',
            '    private final String id;',
            '    public Order(String id) { this.id = id; }',
            '    public String getId() { return id; }',
            '}')

    private static final JavaFileObject CUSTOMER = JavaFileObjects.forSourceLines(
            'examples.ambientnegative.Customer',
            'package examples.ambientnegative;',
            'public class Customer {',
            '}')

    private static final JavaFileObject RESULT = JavaFileObjects.forSourceLines(
            'examples.ambientnegative.Result',
            'package examples.ambientnegative;',
            'public class Result {',
            '    private final String name;',
            '    public Result(String name) { this.name = name; }',
            '    public String getName() { return name; }',
            '}')

    private static final JavaFileObject DUPLICATE_KEY_MAPPER = JavaFileObjects.forSourceLines(
            'examples.ambientnegative.DuplicateKeyMapper',
            'package examples.ambientnegative;',
            'import io.github.joke.percolate.Ambient;',
            'import io.github.joke.percolate.Map;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface DuplicateKeyMapper {',
            '    @Map(target = "name", source = "a.name")',
            '    Result map(Person a, @Ambient("ctx") Order b, @Ambient("ctx") Customer c);',
            '}')

    // ---- unbound-key fixtures --------------------------------------------------------------------------------

    private static final JavaFileObject PRICE = JavaFileObjects.forSourceLines(
            'examples.ambientnegative.Price',
            'package examples.ambientnegative;',
            'public class Price {',
            '    private final int value;',
            '    public Price(int value) { this.value = value; }',
            '    public int getValue() { return value; }',
            '}')

    private static final JavaFileObject RESULT_VIEW = JavaFileObjects.forSourceLines(
            'examples.ambientnegative.ResultView',
            'package examples.ambientnegative;',
            'public class ResultView {',
            '    private final Price price;',
            '    public ResultView(Price price) { this.price = price; }',
            '    public Price getPrice() { return price; }',
            '}')

    private static final JavaFileObject UNBOUND_KEY_MAPPER = JavaFileObjects.forSourceLines(
            'examples.ambientnegative.UnboundKeyMapper',
            'package examples.ambientnegative;',
            'import io.github.joke.percolate.Ambient;',
            'import io.github.joke.percolate.Map;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface UnboundKeyMapper {',
            '    @Map(target = "price", source = "taxFactor")',
            '    ResultView map(Integer taxFactor);',
            '    default Price convert(Integer taxFactor, @Ambient Order order) {',
            '        return new Price(taxFactor + order.getId().length());',
            '    }',
            '}')

    // ---- type-mismatch fixtures -------------------------------------------------------------------------------

    private static final JavaFileObject CUSTOMER_ADDRESS = JavaFileObjects.forSourceLines(
            'examples.ambientnegative.CustomerAddress',
            'package examples.ambientnegative;',
            'public class CustomerAddress {',
            '    private final String street;',
            '    public CustomerAddress(String street) { this.street = street; }',
            '    public String getStreet() { return street; }',
            '}')

    private static final JavaFileObject ORDER_VIEW = JavaFileObjects.forSourceLines(
            'examples.ambientnegative.OrderView',
            'package examples.ambientnegative;',
            'public class OrderView {',
            '    private final AddressView address;',
            '    public OrderView(AddressView address) { this.address = address; }',
            '    public AddressView getAddress() { return address; }',
            '}')

    private static final JavaFileObject ADDRESS_VIEW = JavaFileObjects.forSourceLines(
            'examples.ambientnegative.AddressView',
            'package examples.ambientnegative;',
            'public class AddressView {',
            '    private final String street;',
            '    public AddressView(String street) { this.street = street; }',
            '    public String getStreet() { return street; }',
            '}')

    private static final JavaFileObject MISMATCH_MAPPER = JavaFileObjects.forSourceLines(
            'examples.ambientnegative.MismatchMapper',
            'package examples.ambientnegative;',
            'import io.github.joke.percolate.Ambient;',
            'import io.github.joke.percolate.Map;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface MismatchMapper {',
            '    @Map(target = "address", source = "customer.address")',
            '    OrderView map(Customer customer, @Ambient Order order);',
            '    default AddressView mapAddress(CustomerAddress a, @Ambient("order") Customer order) {',
            '        return new AddressView(a.getStreet());',
            '    }',
            '}')
}
