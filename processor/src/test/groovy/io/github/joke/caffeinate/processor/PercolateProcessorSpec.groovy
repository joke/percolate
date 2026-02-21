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
}
