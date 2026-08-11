package io.github.joke.percolate.test;

import io.github.joke.percolate.MapEnum;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.jetbrains.annotations.VisibleForTesting;

import static java.util.Arrays.stream;
import static javax.lang.model.SourceVersion.latestSupported;

/**
 * A minimal processor whose sole job is proving {@code @MapEnum} is repeatable and readable through the ordinary
 * {@code javax.lang.model} annotation-processing surface (unaffected by its {@code CLASS} retention, which only
 * hides it from {@code java.lang.reflect} at runtime). Every {@code @MapEnum} found on an element is echoed as a
 * {@code NOTE} diagnostic the test asserts on.
 */
@SupportedAnnotationTypes("io.github.joke.percolate.*")
public final class MapEnumProbe extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return latestSupported();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        roundEnv.getRootElements().forEach(root -> {
            emit(root);
            root.getEnclosedElements().forEach(this::emit);
        });
        return false;
    }

    @VisibleForTesting
    void emit(final Element element) {
        stream(element.getAnnotationsByType(MapEnum.class)).forEach(override -> processingEnv
                .getMessager()
                .printMessage(
                        Diagnostic.Kind.NOTE, "MapEnum:" + override.source() + "->" + override.target(), element));
    }
}
