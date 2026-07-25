package io.github.joke.percolate.docs.enummapping;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.MapEnum;
import io.github.joke.percolate.Mapper;

// tag::mapper[]
@Mapper
public interface EnumMappingMapper {

    // Mirrored enums: every constant shares a name with the target, so no @MapEnum is needed.
    Status toStatus(StatusDto s);

    // Differently-named constants: @MapEnum overrides the same-name match. OrderStatus.ARCHIVED has
    // no source constant mapping to it — a target-only constant is never an error.
    @MapEnum(source = "NEW", target = "CREATED")
    @MapEnum(source = "COMPLETED", target = "FULFILLED")
    OrderStatus toOrderStatus(MyStatus s);

    // A bean member of OrderStatus bridges through the declared toOrderStatus(MyStatus) method above,
    // via the existing method-call bridge — no new machinery.
    @Map(target = "status", source = "dto.status")
    Order map(OrderDto dto);
}
// end::mapper[]

// tag::model[]
enum Status {
    CREATED,
    FULFILLED,
    ARCHIVED
}

enum StatusDto {
    CREATED,
    FULFILLED,
    ARCHIVED
}

enum MyStatus {
    NEW,
    COMPLETED
}

enum OrderStatus {
    CREATED,
    FULFILLED,
    ARCHIVED
}

final class OrderDto {
    private final MyStatus status;

    OrderDto(MyStatus status) {
        this.status = status;
    }

    public MyStatus getStatus() {
        return status;
    }
}

final class Order {
    private final OrderStatus status;

    Order(OrderStatus status) {
        this.status = status;
    }

    public OrderStatus getStatus() {
        return status;
    }
}
// end::model[]
