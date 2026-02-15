package io.github.joke.objects.component;

import dagger.Component;
import io.github.joke.objects.immutable.ImmutableSubcomponent;

@Component(modules = ProcessorModule.class)
public interface ProcessorComponent {
    ImmutableSubcomponent.Factory immutable();
}
