package io.github.joke.percolate.docs.abstractclass;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

// tag::mapper[]
@Mapper
public abstract class VehicleMapper {

    @Map(target = "plate", source = "car.licensePlate")
    public abstract CarView map(Car car);
}
// end::mapper[]

// tag::model[]
final class Car {
    private final String licensePlate;

    Car(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public String getLicensePlate() {
        return licensePlate;
    }
}

final class CarView {
    private final String plate;

    CarView(String plate) {
        this.plate = plate;
    }

    public String getPlate() {
        return plate;
    }
}
// end::model[]
