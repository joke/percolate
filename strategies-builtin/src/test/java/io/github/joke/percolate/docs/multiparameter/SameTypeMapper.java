package io.github.joke.percolate.docs.multiparameter;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

// tag::sametype-mapper[]
@Mapper
public interface SameTypeMapper {

    @Map(target = "oldName", source = "before.name")
    @Map(target = "newName", source = "after.name")
    Diff compare(Person before, Person after);
}
// end::sametype-mapper[]

// tag::sametype-model[]
final class Person {
    private final String name;

    Person(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

final class Diff {
    private final String oldName;
    private final String newName;

    Diff(String oldName, String newName) {
        this.oldName = oldName;
        this.newName = newName;
    }

    public String getOldName() {
        return oldName;
    }

    public String getNewName() {
        return newName;
    }
}
// end::sametype-model[]
