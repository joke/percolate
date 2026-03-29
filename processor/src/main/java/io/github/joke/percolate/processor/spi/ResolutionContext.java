package io.github.joke.percolate.processor.spi;

import io.github.joke.percolate.processor.model.DiscoveredMethod;
import java.util.List;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.Value;

@Value
public class ResolutionContext {
    Types types;
    Elements elements;
    List<DiscoveredMethod> methods;
    DiscoveredMethod currentMethod;
}
