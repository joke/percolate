package examples.switches;

import io.github.joke.percolate.MapEnum;
import io.github.joke.percolate.Mapper;

// tag::mapper[]
@Mapper
public interface EnumSwitchMapper {

    @MapEnum(source = "NEW", target = "CREATED")
    OrderStatus toOrderStatus(MyStatus s);
}
// end::mapper[]

enum MyStatus {
    NEW
}

enum OrderStatus {
    CREATED
}
