package io.github.joke.percolate.processor;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.stage.BindingStage;
import io.github.joke.percolate.stage.MethodRegistry;
import io.github.joke.percolate.stage.ParseMapperStage;
import io.github.joke.percolate.stage.RegistrationStage;
import io.github.joke.percolate.stage.WiringStage;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;

@RoundScoped
public class Pipeline {

    private final ParseMapperStage parseMapperStage;
    private final RegistrationStage registrationStage;
    private final BindingStage bindingStage;
    private final WiringStage wiringStage;

    @Inject
    Pipeline(ParseMapperStage parseMapperStage, RegistrationStage registrationStage,
             BindingStage bindingStage, WiringStage wiringStage) {
        this.parseMapperStage = parseMapperStage;
        this.registrationStage = registrationStage;
        this.bindingStage = bindingStage;
        this.wiringStage = wiringStage;
    }

    public void process(TypeElement typeElement) {
        MapperDefinition mapper = parseMapperStage.execute(typeElement);
        MethodRegistry registry = registrationStage.execute(mapper);
        bindingStage.execute(registry);
        wiringStage.execute(registry);
        // ValidateStage, OptimizeStage, CodeGenStage reconnected in future redesign
    }
}
