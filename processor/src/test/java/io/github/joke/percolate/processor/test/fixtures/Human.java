package io.github.joke.percolate.processor.test.fixtures;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class Human {
    private final String firstName;
    private final String lastName;

    public Human(final String firstName, final String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }
}
