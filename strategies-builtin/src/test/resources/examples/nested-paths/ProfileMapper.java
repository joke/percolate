package io.github.joke.percolate.docs.nested;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

// tag::mapper[]
@Mapper
public interface ProfileMapper {

    // A nested SOURCE path hops accessors: getCompany().getAddress().getCity().
    // A nested TARGET path builds the intermediate `location` on the result.
    @Map(target = "location.city", source = "user.company.address.city")
    Profile map(User user);
}
// end::mapper[]

// tag::model[]
final class User {
    private final Company company;

    User(Company company) {
        this.company = company;
    }

    public Company getCompany() {
        return company;
    }
}

final class Company {
    private final Address address;

    Company(Address address) {
        this.address = address;
    }

    public Address getAddress() {
        return address;
    }
}

final class Address {
    private final String city;

    Address(String city) {
        this.city = city;
    }

    public String getCity() {
        return city;
    }
}

final class Profile {
    private final Location location;

    Profile(Location location) {
        this.location = location;
    }

    public Location getLocation() {
        return location;
    }
}

final class Location {
    private final String city;

    Location(String city) {
        this.city = city;
    }

    public String getCity() {
        return city;
    }
}
// end::model[]
