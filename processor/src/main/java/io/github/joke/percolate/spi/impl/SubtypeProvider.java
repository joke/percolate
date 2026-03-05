package io.github.joke.percolate.spi.impl;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.spi.ConversionFragment;
import io.github.joke.percolate.spi.ConversionProvider;
import io.github.joke.percolate.stage.MethodRegistry;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;

@AutoService(ConversionProvider.class)
public final class SubtypeProvider implements ConversionProvider {

    @Override
    public boolean canHandle(
            final TypeMirror source,
            final TypeMirror target,
            final MethodRegistry registry,
            final ProcessingEnvironment env) {
        return env.getTypeUtils().isSubtype(source, target)
                && !env.getTypeUtils().isSameType(source, target);
    }

    @Override
    public ConversionFragment provide(
            final TypeMirror source,
            final TypeMirror target,
            final MethodRegistry registry,
            final ProcessingEnvironment env) {
        return ConversionFragment.of();
    }
}
