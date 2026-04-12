package io.github.joke.percolate.processor;

import dagger.Module;
import dagger.Provides;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;

@Module
@RequiredArgsConstructor
final class ProcessorModule {

    private final ProcessingEnvironment processingEnvironment;

    @Provides
    Elements elements() {
        return processingEnvironment.getElementUtils();
    }

    @Provides
    Types types() {
        return processingEnvironment.getTypeUtils();
    }

    @Provides
    Messager messager() {
        return processingEnvironment.getMessager();
    }

    @Provides
    Filer filer() {
        return processingEnvironment.getFiler();
    }

    @Provides
    ProcessorOptions processorOptions() {
        final var options = processingEnvironment.getOptions();
        final var debugGraphs = Boolean.parseBoolean(options.getOrDefault("percolate.debug.graphs", "false"));
        final var format = options.getOrDefault("percolate.debug.graphs.format", "dot");
        return new ProcessorOptions(debugGraphs, format);
    }
}
