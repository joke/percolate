package io.github.joke.percolate.docs.setters;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

// tag::mapper[]
@Mapper
public interface SetterMapper {

    // A plain JavaBean: no-argument constructor, then one setX call per declared child.
    // Person has no all-arguments constructor and no builder, so nothing else can assemble it.
    @Map(target = "name", source = "dto.name")
    @Map(target = "age", source = "dto.age")
    Person toPerson(PersonDto dto);

    // Containment: Person also exposes setNickname, which this mapping never declares.
    // Only the declared children become setter calls — the rest are simply not called.
    @Map(target = "name", source = "dto.name")
    Person toNameOnlyPerson(PersonDto dto);

    // Inherited setters count: setName is declared on Person, setSalary on Employee.
    @Map(target = "name", source = "dto.name")
    @Map(target = "salary", source = "dto.salary")
    Employee toEmployee(EmployeeDto dto);

    // A setter that returns this rather than void still matches — the helper discards the result.
    @Map(target = "owner", source = "dto.owner")
    Account toAccount(AccountDto dto);
}
// end::mapper[]

// tag::bean[]
class Person {

    private String name = "";

    private int age;

    private String nickname = "unset";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
// end::bean[]

// tag::inherited[]
final class Employee extends Person {

    private long salary;

    public long getSalary() {
        return salary;
    }

    public void setSalary(long salary) {
        this.salary = salary;
    }
}
// end::inherited[]

// tag::fluentsetter[]
final class Account {

    private String owner = "";

    public String getOwner() {
        return owner;
    }

    public Account setOwner(String owner) {
        this.owner = owner;
        return this;
    }
}
// end::fluentsetter[]

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

final class EmployeeDto {

    private final String name;

    private final long salary;

    EmployeeDto(String name, long salary) {
        this.name = name;
        this.salary = salary;
    }

    public String getName() {
        return name;
    }

    public long getSalary() {
        return salary;
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
