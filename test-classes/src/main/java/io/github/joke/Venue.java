package io.github.joke;

import lombok.Value;

@Value
public class Venue {
    String name;
    String street;
    String zipCode;
    String city;
}
