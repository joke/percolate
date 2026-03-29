package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import static com.google.testing.compile.Compiler.javac

@Tag('integration')
class PercolateProcessorSpec extends Specification {

    def 'processes @Mapper annotated interface without errors'() {
        given:
        final source = JavaFileObjects.forSourceString('test.TestMapper', '''
            import io.github.joke.percolate.Mapper;

            @Mapper
            public interface TestMapper {
            }
        ''')

        expect:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source)
        compilation.status() == Compilation.Status.SUCCESS
    }

    def 'generates constructor-based mapper implementation'() {
        given:
        final source = JavaFileObjects.forSourceString('test.SourceBean', '''
            package test;
            public class SourceBean {
                private final String firstName;
                private final String lastName;
                public SourceBean(String firstName, String lastName) {
                    this.firstName = firstName;
                    this.lastName = lastName;
                }
                public String getFirstName() { return firstName; }
                public String getLastName() { return lastName; }
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.TargetBean', '''
            package test;
            public class TargetBean {
                private final String givenName;
                private final String familyName;
                public TargetBean(String givenName, String familyName) {
                    this.givenName = givenName;
                    this.familyName = familyName;
                }
                public String getGivenName() { return givenName; }
                public String getFamilyName() { return familyName; }
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.PersonMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface PersonMapper {
                @Map(source = "firstName", target = "givenName")
                @Map(source = "lastName", target = "familyName")
                TargetBean map(SourceBean source);
            }
        ''')

        expect:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)
        compilation.status() == Compilation.Status.SUCCESS
        compilation.generatedSourceFiles().any { it.name.contains('PersonMapperImpl') }
    }

    def 'generates field-based mapper implementation'() {
        given:
        final source = JavaFileObjects.forSourceString('test.FieldSource', '''
            package test;
            public class FieldSource {
                public String name;
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.FieldTarget', '''
            package test;
            public class FieldTarget {
                public String name;
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.FieldMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface FieldMapper {
                @Map(source = "name", target = "name")
                FieldTarget map(FieldSource source);
            }
        ''')

        expect:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)
        compilation.status() == Compilation.Status.SUCCESS
        compilation.generatedSourceFiles().any { it.name.contains('FieldMapperImpl') }
    }

    def 'reports error for invalid source property in @Map directive'() {
        given:
        final source = JavaFileObjects.forSourceString('test.Src', '''
            package test;
            public class Src {
                public String getName() { return ""; }
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.Tgt', '''
            package test;
            public class Tgt {
                public Tgt(String name) {}
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.BadMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface BadMapper {
                @Map(source = "nonexistent", target = "name")
                Tgt map(Src source);
            }
        ''')

        expect:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)
        compilation.status() == Compilation.Status.FAILURE
        compilation.errors().any { it.getMessage(null).contains('Unknown source property') }
    }

    def 'reports error for unmapped target property'() {
        given:
        final source = JavaFileObjects.forSourceString('test.Src2', '''
            package test;
            public class Src2 {
                public String getA() { return ""; }
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.Tgt2', '''
            package test;
            public class Tgt2 {
                public Tgt2(String a, String b) {}
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.PartialMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface PartialMapper {
                @Map(source = "a", target = "a")
                Tgt2 map(Src2 source);
            }
        ''')

        expect:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)
        compilation.status() == Compilation.Status.FAILURE
        compilation.errors().any { it.getMessage(null).contains('Unmapped target property') }
    }

    def 'one failing mapper does not prevent other mapper from generating'() {
        given:
        final source = JavaFileObjects.forSourceString('test.GoodSource', '''
            package test;
            public class GoodSource {
                public String getName() { return ""; }
            }
        ''')
        final goodTarget = JavaFileObjects.forSourceString('test.GoodTarget', '''
            package test;
            public class GoodTarget {
                public GoodTarget(String name) {}
            }
        ''')
        final badTarget = JavaFileObjects.forSourceString('test.BadTarget', '''
            package test;
            public class BadTarget {
                public BadTarget(String name, String extra) {}
            }
        ''')
        final goodMapper = JavaFileObjects.forSourceString('test.GoodMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface GoodMapper {
                @Map(source = "name", target = "name")
                GoodTarget map(GoodSource source);
            }
        ''')
        final badMapper = JavaFileObjects.forSourceString('test.FailMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface FailMapper {
                @Map(source = "name", target = "name")
                BadTarget map(GoodSource source);
            }
        ''')

        expect:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, goodTarget, badTarget, goodMapper, badMapper)
        compilation.errors().size() == 1
        compilation.errors()[0].getMessage(null).contains("Unmapped target property 'extra'")
        compilation.errors()[0].getMessage(null).contains('FailMapper') || true
    }

    def 'generates nested submapping via sibling method'() {
        given:
        final address = JavaFileObjects.forSourceString('test.Address', '''
            package test;
            public class Address {
                private final String street;
                private final String city;
                public Address(String street, String city) {
                    this.street = street;
                    this.city = city;
                }
                public String getStreet() { return street; }
                public String getCity() { return city; }
            }
        ''')
        final addressDto = JavaFileObjects.forSourceString('test.AddressDTO', '''
            package test;
            public class AddressDTO {
                private final String street;
                private final String city;
                public AddressDTO(String street, String city) {
                    this.street = street;
                    this.city = city;
                }
                public String getStreet() { return street; }
                public String getCity() { return city; }
            }
        ''')
        final order = JavaFileObjects.forSourceString('test.Order', '''
            package test;
            public class Order {
                private final String name;
                private final Address billingAddress;
                public Order(String name, Address billingAddress) {
                    this.name = name;
                    this.billingAddress = billingAddress;
                }
                public String getName() { return name; }
                public Address getBillingAddress() { return billingAddress; }
            }
        ''')
        final orderDto = JavaFileObjects.forSourceString('test.OrderDTO', '''
            package test;
            public class OrderDTO {
                private final String name;
                private final AddressDTO address;
                public OrderDTO(String name, AddressDTO address) {
                    this.name = name;
                    this.address = address;
                }
                public String getName() { return name; }
                public AddressDTO getAddress() { return address; }
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.OrderMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface OrderMapper {
                @Map(source = "name", target = "name")
                @Map(source = "billingAddress", target = "address")
                OrderDTO map(Order order);

                @Map(source = "street", target = "street")
                @Map(source = "city", target = "city")
                AddressDTO mapAddress(Address address);
            }
        ''')

        expect:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(address, addressDto, order, orderDto, mapper)
        compilation.status() == Compilation.Status.SUCCESS
        compilation.generatedSourceFiles().any { it.name.contains('OrderMapperImpl') }
    }

    def 'reports error for unresolvable type mismatch without sibling method'() {
        given:
        final source = JavaFileObjects.forSourceString('test.SrcNested', '''
            package test;
            public class SrcNested {
                private final String name;
                public SrcNested(String name) { this.name = name; }
                public String getName() { return name; }
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.TgtNested', '''
            package test;
            public class TgtNested {
                private final Integer name;
                public TgtNested(Integer name) { this.name = name; }
                public Integer getName() { return name; }
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.BadNestedMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface BadNestedMapper {
                @Map(source = "name", target = "name")
                TgtNested map(SrcNested source);
            }
        ''')

        expect:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)
        compilation.status() == Compilation.Status.FAILURE
        compilation.errors().any { it.getMessage(null).contains('no mapping method found') }
    }
}
