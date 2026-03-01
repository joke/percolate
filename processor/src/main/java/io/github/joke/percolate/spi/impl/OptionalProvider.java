package io.github.joke.percolate.spi.impl;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.graph.node.OptionalUnwrapNode;
import io.github.joke.percolate.graph.node.OptionalWrapNode;
import io.github.joke.percolate.spi.ConversionFragment;
import io.github.joke.percolate.spi.ConversionProvider;
import io.github.joke.percolate.stage.MethodRegistry;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@AutoService(ConversionProvider.class)
public final class OptionalProvider implements ConversionProvider {

    @Override
    public boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return isOptionalType(source) || isOptionalType(target);
    }

    @Override
    public ConversionFragment provide(
            TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env) {
        if (isOptionalType(source) && !isOptionalType(target)) {
            return unwrapFragment(source);
        }
        if (!isOptionalType(source) && isOptionalType(target)) {
            return wrapFragment(target);
        }
        return ConversionFragment.of();
    }

    private static ConversionFragment unwrapFragment(TypeMirror source) {
        List<? extends TypeMirror> args = ((DeclaredType) source).getTypeArguments();
        if (args.isEmpty()) {
            return ConversionFragment.of();
        }
        return ConversionFragment.of(new OptionalUnwrapNode(args.get(0)));
    }

    private static ConversionFragment wrapFragment(TypeMirror target) {
        List<? extends TypeMirror> args = ((DeclaredType) target).getTypeArguments();
        if (args.isEmpty()) {
            return ConversionFragment.of();
        }
        return ConversionFragment.of(new OptionalWrapNode(args.get(0), target));
    }

    private static boolean isOptionalType(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        return "java.util.Optional".equals(((DeclaredType) type).asElement().toString());
    }
}
