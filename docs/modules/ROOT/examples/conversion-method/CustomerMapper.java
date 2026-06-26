package io.github.joke.percolate.docs.conversion;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

// tag::mapper[]
@Mapper
public interface CustomerMapper {

    @Map(target = "address", source = "customer.address")
    CustomerView map(Customer customer);

    // A second mapper method. Percolate generates it AND reuses it as the conversion
    // for the nested `address` field above, emitting `this.toView(customer.getAddress())`.
    @Map(target = "street", source = "address.street")
    AddressView toView(Address address);
}
// end::mapper[]

// tag::model[]
final class Customer {
    private final Address address;

    Customer(Address address) {
        this.address = address;
    }

    public Address getAddress() {
        return address;
    }
}

final class Address {
    private final String street;

    Address(String street) {
        this.street = street;
    }

    public String getStreet() {
        return street;
    }
}

final class CustomerView {
    private final AddressView address;

    CustomerView(AddressView address) {
        this.address = address;
    }

    public AddressView getAddress() {
        return address;
    }
}

final class AddressView {
    private final String street;

    AddressView(String street) {
        this.street = street;
    }

    public String getStreet() {
        return street;
    }
}
// end::model[]
