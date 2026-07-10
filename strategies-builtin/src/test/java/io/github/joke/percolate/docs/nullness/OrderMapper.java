package io.github.joke.percolate.docs.nullness;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;
import org.jspecify.annotations.Nullable;

// tag::mapper[]
@Mapper
public interface OrderMapper {

    // trackingCode is @Nullable on the source but plain (non-null) on the target, and declares no
    // defaultValue — percolate guards the crossing with a requireNonNull check.
    @Map(target = "trackingCode", source = "form.trackingCode")
    Order map(OrderForm form);
}
// end::mapper[]

// tag::model[]
final class OrderForm {
    private final @Nullable String trackingCode;

    OrderForm(@Nullable String trackingCode) {
        this.trackingCode = trackingCode;
    }

    public @Nullable String getTrackingCode() {
        return trackingCode;
    }
}

final class Order {
    private final String trackingCode;

    Order(String trackingCode) {
        this.trackingCode = trackingCode;
    }

    public String getTrackingCode() {
        return trackingCode;
    }
}
// end::model[]
