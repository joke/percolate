package io.github.joke.percolate.stage;

import static javax.lang.model.type.TypeKind.VOID;

import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import javax.inject.Inject;

public final class RegistrationStage {

    @Inject
    RegistrationStage() {}

    public MethodRegistry execute(MapperDefinition mapper) {
        MethodRegistry registry = new MethodRegistry();
        mapper.getMethods().forEach(method -> register(registry, method));
        return registry;
    }

    private static void register(MethodRegistry registry, MethodDefinition method) {
        if (method.getReturnType().getKind() == VOID) {
            return;
        }
        registry.register(method, new RegistryEntry(method, null));
        // graph is null here for all methods; BindingStage will assign graphs to abstract methods.
        // After BindingStage: abstract methods are non-opaque (graph != null), default methods remain permanently
        // opaque (graph stays null).
    }
}
