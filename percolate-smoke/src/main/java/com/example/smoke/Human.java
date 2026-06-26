package com.example.smoke;

public final class Human {

    private final String firstName;
    private final Integer age;

    public Human(final String firstName, final Integer age) {
        this.firstName = firstName;
        this.age = age;
    }

    public String getFirstName() {
        return firstName;
    }

    public Integer getAge() {
        return age;
    }
}
