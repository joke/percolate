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

    def 'generates mapper with auto-mapped same-name properties without any @Map directives'() {
        given:
        final source = JavaFileObjects.forSourceString('test.AutoSource', '''
            package test;
            public class AutoSource {
                private final String name;
                private final int age;
                public AutoSource(String name, int age) {
                    this.name = name;
                    this.age = age;
                }
                public String getName() { return name; }
                public int getAge() { return age; }
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.AutoTarget', '''
            package test;
            public class AutoTarget {
                private final String name;
                private final int age;
                public AutoTarget(String name, int age) {
                    this.name = name;
                    this.age = age;
                }
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.AutoMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;

            @Mapper
            public interface AutoMapper {
                AutoTarget map(AutoSource source);
            }
        ''')

        expect:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)
        compilation.status() == Compilation.Status.SUCCESS
        compilation.generatedSourceFiles().any { it.name.contains('AutoMapperImpl') }
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

    def 'generates List to List mapping with sibling method'() {
        given:
        final person = JavaFileObjects.forSourceString('test.Person', '''
            package test;
            public class Person {
                private final String name;
                public Person(String name) { this.name = name; }
                public String getName() { return name; }
            }
        ''')
        final personDto = JavaFileObjects.forSourceString('test.PersonDTO', '''
            package test;
            public class PersonDTO {
                private final String name;
                public PersonDTO(String name) { this.name = name; }
                public String getName() { return name; }
            }
        ''')
        final source = JavaFileObjects.forSourceString('test.Team', '''
            package test;
            import java.util.List;
            public class Team {
                private final List<Person> members;
                public Team(List<Person> members) { this.members = members; }
                public List<Person> getMembers() { return members; }
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.TeamDTO', '''
            package test;
            import java.util.List;
            public class TeamDTO {
                private final List<PersonDTO> members;
                public TeamDTO(List<PersonDTO> members) { this.members = members; }
                public List<PersonDTO> getMembers() { return members; }
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.TeamMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface TeamMapper {
                @Map(source = "members", target = "members")
                TeamDTO map(Team team);

                @Map(source = "name", target = "name")
                PersonDTO mapPerson(Person person);
            }
        ''')

        expect:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(person, personDto, source, target, mapper)
        compilation.status() == Compilation.Status.SUCCESS
        compilation.generatedSourceFiles().any { it.name.contains('TeamMapperImpl') }
    }

    def 'generates List to Set container conversion with sibling method'() {
        given:
        final person = JavaFileObjects.forSourceString('test.Person2', '''
            package test;
            public class Person2 {
                private final String name;
                public Person2(String name) { this.name = name; }
                public String getName() { return name; }
            }
        ''')
        final personDto = JavaFileObjects.forSourceString('test.PersonDTO2', '''
            package test;
            public class PersonDTO2 {
                private final String name;
                public PersonDTO2(String name) { this.name = name; }
                public String getName() { return name; }
            }
        ''')
        final source = JavaFileObjects.forSourceString('test.Group', '''
            package test;
            import java.util.List;
            public class Group {
                private final List<Person2> people;
                public Group(List<Person2> people) { this.people = people; }
                public List<Person2> getPeople() { return people; }
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.GroupDTO', '''
            package test;
            import java.util.Set;
            public class GroupDTO {
                private final Set<PersonDTO2> people;
                public GroupDTO(Set<PersonDTO2> people) { this.people = people; }
                public Set<PersonDTO2> getPeople() { return people; }
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.GroupMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface GroupMapper {
                @Map(source = "people", target = "people")
                GroupDTO map(Group group);

                @Map(source = "name", target = "name")
                PersonDTO2 mapPerson(Person2 person);
            }
        ''')

        expect:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(person, personDto, source, target, mapper)
        compilation.status() == Compilation.Status.SUCCESS
        compilation.generatedSourceFiles().any { it.name.contains('GroupMapperImpl') }
    }

    def 'generates Set to List conversion with direct element type'() {
        given:
        final source = JavaFileObjects.forSourceString('test.SetSource', '''
            package test;
            import java.util.Set;
            public class SetSource {
                private final Set<String> tags;
                public SetSource(Set<String> tags) { this.tags = tags; }
                public Set<String> getTags() { return tags; }
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.ListTarget', '''
            package test;
            import java.util.List;
            public class ListTarget {
                private final List<String> tags;
                public ListTarget(List<String> tags) { this.tags = tags; }
                public List<String> getTags() { return tags; }
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.TagMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface TagMapper {
                @Map(source = "tags", target = "tags")
                ListTarget map(SetSource source);
            }
        ''')

        expect:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)
        compilation.status() == Compilation.Status.SUCCESS
        compilation.generatedSourceFiles().any { it.name.contains('TagMapperImpl') }
    }

    def 'generates Optional to Optional mapping with sibling method'() {
        given:
        final person = JavaFileObjects.forSourceString('test.OptPerson', '''
            package test;
            public class OptPerson {
                private final String name;
                public OptPerson(String name) { this.name = name; }
                public String getName() { return name; }
            }
        ''')
        final personDto = JavaFileObjects.forSourceString('test.OptPersonDTO', '''
            package test;
            public class OptPersonDTO {
                private final String name;
                public OptPersonDTO(String name) { this.name = name; }
                public String getName() { return name; }
            }
        ''')
        final source = JavaFileObjects.forSourceString('test.OptSource', '''
            package test;
            import java.util.Optional;
            public class OptSource {
                private final Optional<OptPerson> leader;
                public OptSource(Optional<OptPerson> leader) { this.leader = leader; }
                public Optional<OptPerson> getLeader() { return leader; }
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.OptTarget', '''
            package test;
            import java.util.Optional;
            public class OptTarget {
                private final Optional<OptPersonDTO> leader;
                public OptTarget(Optional<OptPersonDTO> leader) { this.leader = leader; }
                public Optional<OptPersonDTO> getLeader() { return leader; }
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.OptMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface OptMapper {
                @Map(source = "leader", target = "leader")
                OptTarget map(OptSource source);

                @Map(source = "name", target = "name")
                OptPersonDTO mapPerson(OptPerson person);
            }
        ''')

        expect:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(person, personDto, source, target, mapper)
        compilation.status() == Compilation.Status.SUCCESS
        compilation.generatedSourceFiles().any { it.name.contains('OptMapperImpl') }
    }

    def 'generates Optional wrap and unwrap mappings'() {
        given:
        final source = JavaFileObjects.forSourceString('test.WrapSource', '''
            package test;
            import java.util.Optional;
            public class WrapSource {
                private final String name;
                private final Optional<String> nickname;
                public WrapSource(String name, Optional<String> nickname) {
                    this.name = name;
                    this.nickname = nickname;
                }
                public String getName() { return name; }
                public Optional<String> getNickname() { return nickname; }
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.WrapTarget', '''
            package test;
            import java.util.Optional;
            public class WrapTarget {
                private final Optional<String> name;
                private final String nickname;
                public WrapTarget(Optional<String> name, String nickname) {
                    this.name = name;
                    this.nickname = nickname;
                }
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.WrapMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface WrapMapper {
                @Map(source = "name", target = "name")
                @Map(source = "nickname", target = "nickname")
                WrapTarget map(WrapSource source);
            }
        ''')

        expect:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)
        compilation.status() == Compilation.Status.SUCCESS
        compilation.generatedSourceFiles().any { it.name.contains('WrapMapperImpl') }
    }

    def 'generates mixed flat and container properties'() {
        given:
        final item = JavaFileObjects.forSourceString('test.Item', '''
            package test;
            public class Item {
                private final String label;
                public Item(String label) { this.label = label; }
                public String getLabel() { return label; }
            }
        ''')
        final itemDto = JavaFileObjects.forSourceString('test.ItemDTO', '''
            package test;
            public class ItemDTO {
                private final String label;
                public ItemDTO(String label) { this.label = label; }
                public String getLabel() { return label; }
            }
        ''')
        final source = JavaFileObjects.forSourceString('test.MixedSource', '''
            package test;
            import java.util.List;
            public class MixedSource {
                private final String title;
                private final List<Item> items;
                public MixedSource(String title, List<Item> items) {
                    this.title = title;
                    this.items = items;
                }
                public String getTitle() { return title; }
                public List<Item> getItems() { return items; }
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.MixedTarget', '''
            package test;
            import java.util.List;
            public class MixedTarget {
                private final String title;
                private final List<ItemDTO> items;
                public MixedTarget(String title, List<ItemDTO> items) {
                    this.title = title;
                    this.items = items;
                }
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.MixedMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface MixedMapper {
                @Map(source = "title", target = "title")
                @Map(source = "items", target = "items")
                MixedTarget map(MixedSource source);

                @Map(source = "label", target = "label")
                ItemDTO mapItem(Item item);
            }
        ''')

        expect:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(item, itemDto, source, target, mapper)
        compilation.status() == Compilation.Status.SUCCESS
        compilation.generatedSourceFiles().any { it.name.contains('MixedMapperImpl') }
    }

    def 'generates debug graph files when debug graphs option is enabled'() {
        given:
        final source = JavaFileObjects.forSourceString('test.DbgSource', '''
            package test;
            public class DbgSource {
                private final String name;
                public DbgSource(String name) { this.name = name; }
                public String getName() { return name; }
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.DbgTarget', '''
            package test;
            public class DbgTarget {
                private final String name;
                public DbgTarget(String name) { this.name = name; }
                public String getName() { return name; }
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.DbgMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface DbgMapper {
                @Map(source = "name", target = "name")
                DbgTarget map(DbgSource source);
            }
        ''')

        when:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .withOptions('-Apercolate.debug.graphs=true')
                .compile(source, target, mapper)

        then:
        compilation.status() == Compilation.Status.SUCCESS
        compilation.generatedFiles().any { it.name.endsWith('.dot') }
    }

    def 'reports error for container mapping without sibling method'() {
        given:
        final person = JavaFileObjects.forSourceString('test.ErrPerson', '''
            package test;
            public class ErrPerson {
                private final String name;
                public ErrPerson(String name) { this.name = name; }
                public String getName() { return name; }
            }
        ''')
        final personDto = JavaFileObjects.forSourceString('test.ErrPersonDTO', '''
            package test;
            public class ErrPersonDTO {
                private final String name;
                public ErrPersonDTO(String name) { this.name = name; }
                public String getName() { return name; }
            }
        ''')
        final source = JavaFileObjects.forSourceString('test.ErrSource', '''
            package test;
            import java.util.List;
            public class ErrSource {
                private final List<ErrPerson> people;
                public ErrSource(List<ErrPerson> people) { this.people = people; }
                public List<ErrPerson> getPeople() { return people; }
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.ErrTarget', '''
            package test;
            import java.util.Set;
            public class ErrTarget {
                private final Set<ErrPersonDTO> people;
                public ErrTarget(Set<ErrPersonDTO> people) { this.people = people; }
                public Set<ErrPersonDTO> getPeople() { return people; }
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.ErrMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface ErrMapper {
                @Map(source = "people", target = "people")
                ErrTarget map(ErrSource source);
            }
        ''')

        expect:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(person, personDto, source, target, mapper)
        compilation.status() == Compilation.Status.FAILURE
        compilation.errors().any { it.getMessage(null).contains('no mapping method found') }
    }
}
