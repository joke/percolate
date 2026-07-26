package io.github.joke.percolate.spi.builtins.deferral;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

/**
 * Test-only co-processor standing in for Lombok: on its first round it writes {@code examples.deferral.Widget}, a
 * brand-new source file invisible to every processor (including {@code PercolateProcessor}) until the following
 * round — the same shape as a Lombok-injected member appearing on a later round of the same compilation.
 */
@SupportedAnnotationTypes("*")
public final class WidgetGeneratingProcessor extends AbstractProcessor {

    private boolean generated;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        if (!generated && !roundEnv.processingOver()) {
            generated = true;
            writeWidget();
        }
        return false;
    }

    private void writeWidget() {
        try {
            final var file = processingEnv.getFiler().createSourceFile("examples.deferral.Widget");
            try (var writer = file.openWriter()) {
                writer.write("""
                        package examples.deferral;
                        public final class Widget {
                            public String getValue() { return "generated"; }
                        }
                        """);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
