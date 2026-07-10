package examples.switches;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;
import org.jspecify.annotations.NullMarked;

// tag::mapper[]
@Mapper
public interface NullableMapper {

    // trackingCode is annotated with a THIRD-PARTY @Nullable (examples.switches.CustomNullable), not jspecify's.
    // Percolate only recognises it as a nullness marker when it is registered via percolate.nullable.annotations.
    @Map(target = "trackingCode", source = "form.trackingCode")
    Shipment map(ShipmentForm form);
}
// end::mapper[]

// tag::model[]
@NullMarked
final class ShipmentForm {
    private final @CustomNullable String trackingCode;

    ShipmentForm(@CustomNullable String trackingCode) {
        this.trackingCode = trackingCode;
    }

    public @CustomNullable String getTrackingCode() {
        return trackingCode;
    }
}

@NullMarked
final class Shipment {
    private final String trackingCode;

    Shipment(String trackingCode) {
        this.trackingCode = trackingCode;
    }

    public String getTrackingCode() {
        return trackingCode;
    }
}
// end::model[]
