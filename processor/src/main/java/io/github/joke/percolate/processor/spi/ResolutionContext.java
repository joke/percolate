package io.github.joke.percolate.processor.spi;

import io.github.joke.percolate.MapOptKey;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.Value;

@Value
public class ResolutionContext {
    Types types;
    Elements elements;
    TypeElement mapperType;
    ExecutableElement currentMethod;
    Map<MapOptKey, String> options;

    public Optional<String> getOption(final MapOptKey key) {
        return Optional.ofNullable(options.get(key));
    }
}
