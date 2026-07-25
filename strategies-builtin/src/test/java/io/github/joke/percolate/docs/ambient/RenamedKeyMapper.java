package io.github.joke.percolate.docs.ambient;

import io.github.joke.percolate.Ambient;
import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

// tag::renamed-mapper[]
@Mapper
public interface RenamedKeyMapper {

    @Map(target = "name", source = "person")
    PersonView map(Person person, @Ambient Locator simon);

    // The consumer names its own parameter `loc`, but `@Ambient("simon")` still binds the `simon` key the
    // top-level method published — renaming is purely local to this parameter.
    default String describe(Person person, @Ambient("simon") Locator loc) {
        return person.getName() + "@" + loc.getCoordinates();
    }
}
// end::renamed-mapper[]

// tag::renamed-model[]
final class Person {
    private final String name;

    Person(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

final class Locator {
    private final String coordinates;

    Locator(String coordinates) {
        this.coordinates = coordinates;
    }

    public String getCoordinates() {
        return coordinates;
    }
}

final class PersonView {
    private final String name;

    PersonView(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
// end::renamed-model[]
