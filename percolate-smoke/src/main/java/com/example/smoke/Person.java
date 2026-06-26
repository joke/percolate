package com.example.smoke;

public final class Person {

    private final String firstName;
    private final int age;

    public Person(final String firstName, final int age) {
        this.firstName = firstName;
        this.age = age;
    }

    public String getFirstName() {
        return firstName;
    }

    public int getAge() {
        return age;
    }
}
