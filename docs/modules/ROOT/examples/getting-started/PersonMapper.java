package io.github.joke.percolate.docs.gettingstarted;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

// tag::mapper[]
@Mapper
public interface PersonMapper {

    @Map(target = "firstName", source = "person.firstName")
    Human map(Person person);
}
// end::mapper[]

// tag::model[]
final class Person {
    private final String firstName;

    Person(String firstName) {
        this.firstName = firstName;
    }

    public String getFirstName() {
        return firstName;
    }
}

final class Human {
    private final String firstName;

    Human(String firstName) {
        this.firstName = firstName;
    }

    public String getFirstName() {
        return firstName;
    }
}
// end::model[]
