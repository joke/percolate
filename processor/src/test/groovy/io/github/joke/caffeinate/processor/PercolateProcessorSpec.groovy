package io.github.joke.caffeinate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class PercolateProcessorSpec extends Specification {

    def "generates impl that maps List<A> to List<B> via converter method"() {
        given:
        def actorSrc = JavaFileObjects.forSourceLines("io.example.Actor",
            "package io.example;",
            "public final class Actor {",
            "    private final String name;",
            "    public Actor(String name) { this.name = name; }",
            "    public String getName() { return name; }",
            "}")
        def actorDtoSrc = JavaFileObjects.forSourceLines("io.example.ActorDto",
            "package io.example;",
            "public final class ActorDto {",
            "    private final String name;",
            "    public ActorDto(String name) { this.name = name; }",
            "    public String getName() { return name; }",
            "}")
        def showSrc = JavaFileObjects.forSourceLines("io.example.Show",
            "package io.example;",
            "import java.util.List;",
            "public final class Show {",
            "    private final List<Actor> actors;",
            "    public Show(List<Actor> actors) { this.actors = actors; }",
            "    public List<Actor> getActors() { return actors; }",
            "}")
        def showDtoSrc = JavaFileObjects.forSourceLines("io.example.ShowDto",
            "package io.example;",
            "import java.util.List;",
            "public final class ShowDto {",
            "    private final List<ActorDto> actors;",
            "    public ShowDto(List<ActorDto> actors) { this.actors = actors; }",
            "    public List<ActorDto> getActors() { return actors; }",
            "}")
        def mapperSrc = JavaFileObjects.forSourceLines("io.example.ShowMapper",
            "package io.example;",
            "import io.github.joke.caffeinate.Mapper;",
            "@Mapper",
            "public interface ShowMapper {",
            "    ShowDto map(Show show);",
            "    ActorDto mapActor(Actor actor);",
            "}")

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(actorSrc, actorDtoSrc, showSrc, showDtoSrc, mapperSrc)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation).generatedSourceFile("io.example.ShowMapperImpl")
            .contentsAsUtf8String()
            .contains("stream()")
    }

    def "generates impl that maps same-named enum constants"() {
        given:
        def statusSrc = JavaFileObjects.forSourceLines("io.example.Status",
            "package io.example;",
            "public enum Status { ACTIVE, INACTIVE }")
        def statusDtoSrc = JavaFileObjects.forSourceLines("io.example.StatusDto",
            "package io.example;",
            "public enum StatusDto { ACTIVE, INACTIVE }")
        def mapperSrc = JavaFileObjects.forSourceLines("io.example.StatusMapper",
            "package io.example;",
            "import io.github.joke.caffeinate.Mapper;",
            "@Mapper",
            "public interface StatusMapper {",
            "    StatusDto map(Status status);",
            "}")

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(statusSrc, statusDtoSrc, mapperSrc)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation).generatedSourceFile("io.example.StatusMapperImpl")
            .contentsAsUtf8String()
            .contains("valueOf")
    }

    def "generates impl that maps Optional<A> to Optional<B> via converter method"() {
        given:
        def addressSrc = JavaFileObjects.forSourceLines("io.example.Address",
            "package io.example;",
            "public final class Address {",
            "    private final String city;",
            "    public Address(String city) { this.city = city; }",
            "    public String getCity() { return city; }",
            "}")
        def addressDtoSrc = JavaFileObjects.forSourceLines("io.example.AddressDto",
            "package io.example;",
            "public final class AddressDto {",
            "    private final String city;",
            "    public AddressDto(String city) { this.city = city; }",
            "    public String getCity() { return city; }",
            "}")
        def personSrc = JavaFileObjects.forSourceLines("io.example.PersonOpt",
            "package io.example;",
            "import java.util.Optional;",
            "public final class PersonOpt {",
            "    private final Optional<Address> address;",
            "    public PersonOpt(Optional<Address> address) { this.address = address; }",
            "    public Optional<Address> getAddress() { return address; }",
            "}")
        def personDtoSrc = JavaFileObjects.forSourceLines("io.example.PersonOptDto",
            "package io.example;",
            "import java.util.Optional;",
            "public final class PersonOptDto {",
            "    private final Optional<AddressDto> address;",
            "    public PersonOptDto(Optional<AddressDto> address) { this.address = address; }",
            "    public Optional<AddressDto> getAddress() { return address; }",
            "}")
        def mapperSrc = JavaFileObjects.forSourceLines("io.example.PersonOptMapper",
            "package io.example;",
            "import io.github.joke.caffeinate.Mapper;",
            "@Mapper",
            "public interface PersonOptMapper {",
            "    PersonOptDto map(PersonOpt person);",
            "    AddressDto mapAddress(Address address);",
            "}")

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(addressSrc, addressDtoSrc, personSrc, personDtoSrc, mapperSrc)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation).generatedSourceFile("io.example.PersonOptMapperImpl")
            .contentsAsUtf8String()
            .contains(".map(this::")
    }

    def "generates impl for mapper with getter-based name-matched properties"() {
        given:
        def personSrc = JavaFileObjects.forSourceLines("io.example.Person",
            "package io.example;",
            "public final class Person {",
            "    private final String name;",
            "    public Person(String name) { this.name = name; }",
            "    public String getName() { return name; }",
            "}")
        def mapperSrc = JavaFileObjects.forSourceLines("io.example.PersonMapper",
            "package io.example;",
            "import io.github.joke.caffeinate.Mapper;",
            "@Mapper",
            "public interface PersonMapper {",
            "    Person map(Person source);",
            "}")

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(personSrc, mapperSrc)

        then:
        assertThat(compilation).succeeded()
        assertThat(compilation).generatedSourceFile("io.example.PersonMapperImpl")
            .contentsAsUtf8String()
            .contains("getName()")
    }

    def "emits error when @Map source path does not resolve"() {
        given:
        // Source bean has "firstName" but target expects "fullName" — no name-match.
        // @Map claims to map "fullName" from "src.nonExistentField", which doesn't exist.
        // With the bad source path, @Map can't cover "fullName", and name-match also fails.
        def sourceSrc = JavaFileObjects.forSourceLines("io.example.NameSource",
            "package io.example;",
            "public final class NameSource {",
            "    private final String firstName;",
            "    public NameSource(String firstName) { this.firstName = firstName; }",
            "    public String getFirstName() { return firstName; }",
            "}")
        def targetSrc = JavaFileObjects.forSourceLines("io.example.NameTarget",
            "package io.example;",
            "public final class NameTarget {",
            "    private final String fullName;",
            "    public NameTarget(String fullName) { this.fullName = fullName; }",
            "    public String getFullName() { return fullName; }",
            "}")
        def mapperSrc = JavaFileObjects.forSourceLines("io.example.NameBadMapper",
            "package io.example;",
            "import io.github.joke.caffeinate.Mapper;",
            "import io.github.joke.caffeinate.Map;",
            "@Mapper",
            "public interface NameBadMapper {",
            "    @Map(target = \"fullName\", source = \"src.nonExistentField\")",
            "    NameTarget map(NameSource src);",
            "}")

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(sourceSrc, targetSrc, mapperSrc)

        then:
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("Partial resolution graph")
    }

    def "emits partial resolution graph when mapping is incomplete"() {
        given:
        def show2Src = JavaFileObjects.forSourceLines("io.example.Show2",
            "package io.example;",
            "import java.util.List;",
            "public final class Show2 {",
            "    private final String title;",
            "    public Show2(String title) { this.title = title; }",
            "    public String getTitle() { return title; }",
            "}")
        def show2DtoSrc = JavaFileObjects.forSourceLines("io.example.Show2Dto",
            "package io.example;",
            "import java.util.List;",
            "public final class Show2Dto {",
            "    private final String title;",
            "    private final String description;",
            "    public Show2Dto(String title, String description) { this.title = title; this.description = description; }",
            "    public String getTitle() { return title; }",
            "    public String getDescription() { return description; }",
            "}")
        def mapperSrc = JavaFileObjects.forSourceLines("io.example.Show2Mapper",
            "package io.example;",
            "import io.github.joke.caffeinate.Mapper;",
            "@Mapper",
            "public interface Show2Mapper {",
            "    Show2Dto map(Show2 show);",
            "}")

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(show2Src, show2DtoSrc, mapperSrc)

        then:
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("Partial resolution graph")
        assertThat(compilation).hadErrorContaining("description")
        assertThat(compilation).hadErrorContaining("\u2717")
        assertThat(compilation).hadErrorContaining("\u2713")
    }

    def "emits error when @Map source path segment does not exist"() {
        given:
        def src = JavaFileObjects.forSourceLines("io.example.Src",
            "package io.example;",
            "public final class Src {",
            "    public String getName() { return null; }",
            "}")
        def tgt = JavaFileObjects.forSourceLines("io.example.Tgt",
            "package io.example;",
            "public final class Tgt {",
            "    private final String title;",
            "    public Tgt(String title) { this.title = title; }",
            "    public String getTitle() { return title; }",
            "}")
        def mapper = JavaFileObjects.forSourceLines("io.example.BadSourceMapper",
            "package io.example;",
            "import io.github.joke.caffeinate.Mapper;",
            "import io.github.joke.caffeinate.Map;",
            "@Mapper",
            "public interface BadSourceMapper {",
            "    @Map(target = \"title\", source = \"src.noSuchProp\")",
            "    Tgt map(Src src);",
            "}")

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(src, tgt, mapper)

        then:
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("noSuchProp")
    }

    def "emits error when @Map target path segment does not exist"() {
        given:
        def src = JavaFileObjects.forSourceLines("io.example.Src2",
            "package io.example;",
            "public final class Src2 {",
            "    public String getName() { return null; }",
            "}")
        def tgt = JavaFileObjects.forSourceLines("io.example.Tgt2",
            "package io.example;",
            "public final class Tgt2 {",
            "    private final String name;",
            "    public Tgt2(String name) { this.name = name; }",
            "    public String getName() { return name; }",
            "}")
        def mapper = JavaFileObjects.forSourceLines("io.example.BadTargetMapper",
            "package io.example;",
            "import io.github.joke.caffeinate.Mapper;",
            "import io.github.joke.caffeinate.Map;",
            "@Mapper",
            "public interface BadTargetMapper {",
            "    @Map(target = \"noSuchProp\", source = \"src2.name\")",
            "    Tgt2 map(Src2 src2);",
            "}")

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(src, tgt, mapper)

        then:
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("noSuchProp")
    }

    def "expands wildcard source and maps matching named properties"() {
        given:
        def order = JavaFileObjects.forSourceLines("io.example.SimpleOrder",
            "package io.example;",
            "public final class SimpleOrder {",
            "    private final long orderId;",
            "    private final String orderName;",
            "    public SimpleOrder(long orderId, String orderName) { this.orderId = orderId; this.orderName = orderName; }",
            "    public long getOrderId() { return orderId; }",
            "    public String getOrderName() { return orderName; }",
            "}")
        def flat = JavaFileObjects.forSourceLines("io.example.FlatOrder",
            "package io.example;",
            "public final class FlatOrder {",
            "    private final long orderId;",
            "    private final String orderName;",
            "    public FlatOrder(long orderId, String orderName) { this.orderId = orderId; this.orderName = orderName; }",
            "    public long getOrderId() { return orderId; }",
            "    public String getOrderName() { return orderName; }",
            "}")
        def mapper = JavaFileObjects.forSourceLines("io.example.WildcardMapper",
            "package io.example;",
            "import io.github.joke.caffeinate.Mapper;",
            "import io.github.joke.caffeinate.Map;",
            "@Mapper",
            "public interface WildcardMapper {",
            "    @Map(target = \".\", source = \"order.*\")",
            "    FlatOrder map(SimpleOrder order);",
            "}")

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(order, flat, mapper)

        then:
        assertThat(compilation).succeeded()
        def impl = assertThat(compilation).generatedSourceFile("io.example.WildcardMapperImpl")
        impl.contentsAsUtf8String().contains("getOrderId()")
        impl.contentsAsUtf8String().contains("getOrderName()")
    }

    def "emits error when multi-param method has uncovered target property"() {
        given:
        def a = JavaFileObjects.forSourceLines("io.example.PartA",
            "package io.example;",
            "public final class PartA { public String getFoo() { return null; } }")
        def b = JavaFileObjects.forSourceLines("io.example.PartB",
            "package io.example;",
            "public final class PartB { public String getBar() { return null; } }")
        def result = JavaFileObjects.forSourceLines("io.example.Combined",
            "package io.example;",
            "public final class Combined {",
            "    private final String foo;",
            "    private final String bar;",
            "    public Combined(String foo, String bar) { this.foo = foo; this.bar = bar; }",
            "    public String getFoo() { return foo; }",
            "    public String getBar() { return bar; }",
            "}")
        // Multi-param, no @Map — both foo and bar are uncovered with the new rule
        def mapper = JavaFileObjects.forSourceLines("io.example.MultiParamMapper",
            "package io.example;",
            "import io.github.joke.caffeinate.Mapper;",
            "@Mapper",
            "public interface MultiParamMapper {",
            "    Combined map(PartA a, PartB b);",
            "}")

        when:
        def compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(a, b, result, mapper)

        then:
        assertThat(compilation).failed()
    }
}
