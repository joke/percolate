package io.github.joke.percolate.processor.discover;

/**
 * A tiny non-JDK fixture for {@code TypeSpaceAdapterSpec}: a getter-bearing DTO with a constructor, whose
 * declared members the discovery adapter mirrors into a {@code TypeDecl}.
 */
public class PersonDto {

    private final String name;
    private final int age;

    public PersonDto(final String name, final int age) {
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
