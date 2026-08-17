package io.github.joke.percolate.docs.builders;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

// tag::mapper[]
@Mapper
public interface BuilderMapper {

    // Fluent (Lombok @Builder, AutoValue, most hand-written builders):
    // Person.builder().name(…).age(…).build()
    @Map(target = "name", source = "dto.name")
    @Map(target = "age", source = "dto.age")
    Person toPerson(PersonDto dto);

    // Protobuf: Message.newBuilder().setSubject(…).build()
    @Map(target = "subject", source = "dto.subject")
    Message toMessage(MessageDto dto);

    // With-style: Account.builder().withOwner(…).build()
    @Map(target = "owner", source = "dto.owner")
    Account toAccount(AccountDto dto);

    // Side-located: the builder is a sibling type, constructed directly.
    // new WidgetBuilder().label(…).build()
    @Map(target = "label", source = "dto.label")
    Widget toWidget(WidgetDto dto);

    // Containment: Person's builder also offers nickname() and email(), which this mapping never
    // declares. Only the declared children become setter calls — the rest are simply not called.
    @Map(target = "name", source = "dto.name")
    Person toNameOnlyPerson(PersonDto dto);
}
// end::mapper[]

// tag::fluent[]
final class Person {

    private final String name;

    private final int age;

    private final String nickname;

    private Person(PersonBuilder builder) {
        this.name = builder.name;
        this.age = builder.age;
        this.nickname = builder.nickname;
    }

    static PersonBuilder builder() {
        return new PersonBuilder();
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public String getNickname() {
        return nickname;
    }

    static final class PersonBuilder {

        private String name = "";

        private int age;

        private String nickname = "unset";

        PersonBuilder name(String value) {
            this.name = value;
            return this;
        }

        PersonBuilder age(int value) {
            this.age = value;
            return this;
        }

        PersonBuilder nickname(String value) {
            this.nickname = value;
            return this;
        }

        Person build() {
            return new Person(this);
        }
    }
}
// end::fluent[]

// tag::protobuf[]
final class Message {

    private final String subject;

    private Message(String subject) {
        this.subject = subject;
    }

    static Builder newBuilder() {
        return new Builder();
    }

    public String getSubject() {
        return subject;
    }

    static final class Builder {

        private String subject = "";

        Builder setSubject(String value) {
            this.subject = value;
            return this;
        }

        Message build() {
            return new Message(subject);
        }
    }
}
// end::protobuf[]

// tag::with[]
final class Account {

    private final String owner;

    private Account(String owner) {
        this.owner = owner;
    }

    static AccountBuilder builder() {
        return new AccountBuilder();
    }

    public String getOwner() {
        return owner;
    }

    static final class AccountBuilder {

        private String owner = "";

        AccountBuilder withOwner(String value) {
            this.owner = value;
            return this;
        }

        Account build() {
            return new Account(owner);
        }
    }
}
// end::with[]

// tag::sidelocated[]
final class Widget {

    private final String label;

    // Constructed only through its sibling builder: no constructor takes a `label`, so the
    // declared-children gate rules constructor assembly out and the side-located builder is the way in.
    Widget(WidgetBuilder builder) {
        this.label = builder.label;
    }

    public String getLabel() {
        return label;
    }
}

final class WidgetBuilder {

    String label = "";

    WidgetBuilder label(String value) {
        this.label = value;
        return this;
    }

    Widget build() {
        return new Widget(this);
    }
}
// end::sidelocated[]

final class PersonDto {

    private final String name;

    private final int age;

    PersonDto(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }
}

final class MessageDto {

    private final String subject;

    MessageDto(String subject) {
        this.subject = subject;
    }

    public String getSubject() {
        return subject;
    }
}

final class AccountDto {

    private final String owner;

    AccountDto(String owner) {
        this.owner = owner;
    }

    public String getOwner() {
        return owner;
    }
}

final class WidgetDto {

    private final String label;

    WidgetDto(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
