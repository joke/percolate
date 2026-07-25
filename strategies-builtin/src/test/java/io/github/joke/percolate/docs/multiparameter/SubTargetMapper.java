package io.github.joke.percolate.docs.multiparameter;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

// tag::subtarget-mapper[]
@Mapper
public interface SubTargetMapper {

    @Map(target = "summary.customerName", source = "customer.name")
    @Map(target = "summary.street", source = "address.street")
    OrderSummaryView map(Customer customer, Address address);
}
// end::subtarget-mapper[]

// tag::subtarget-model[]
final class OrderSummaryView {
    private final Summary summary;

    OrderSummaryView(Summary summary) {
        this.summary = summary;
    }

    public Summary getSummary() {
        return summary;
    }
}

final class Summary {
    private final String customerName;
    private final String street;

    Summary(String customerName, String street) {
        this.customerName = customerName;
        this.street = street;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getStreet() {
        return street;
    }
}
// end::subtarget-model[]
