package io.github.joke.percolate.processor.transform;

import io.github.joke.percolate.processor.model.DiscoveredMethod;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.TypeElement;
import lombok.Value;

@Value
public class ResolvedModel {
    TypeElement mapperType;
    List<DiscoveredMethod> methods;
    Map<DiscoveredMethod, List<ResolvedMapping>> methodMappings;
}
