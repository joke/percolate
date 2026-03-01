package io.github.joke.percolate.processor;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.stage.BindingStage;
import io.github.joke.percolate.stage.MethodRegistry;
import io.github.joke.percolate.stage.RegistrationStage;
import io.github.joke.percolate.stage.WiringStage;
import javax.inject.Inject;

@RoundScoped
public class Pipeline {

    private final RegistrationStage registrationStage;
    private final BindingStage bindingStage;
    private final WiringStage wiringStage;

    @Inject
    Pipeline(RegistrationStage registrationStage, BindingStage bindingStage, WiringStage wiringStage) {
        this.registrationStage = registrationStage;
        this.bindingStage = bindingStage;
        this.wiringStage = wiringStage;
    }

    public void process(MapperDefinition mapper) {
        MethodRegistry registry = registrationStage.execute(mapper);
        registry = bindingStage.execute(registry);
        wiringStage.execute(registry);
        // ValidateStage, OptimizeStage, CodeGenStage reconnected in future redesign
    }
}
