package io.github.joke.percolate.processor.transform;

import io.github.joke.percolate.processor.model.MappingMethodModel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import lombok.Value;

@Value
public class ResolvedModel {
    TypeElement mapperType;
    List<MappingMethodModel> methods;
    Map<MappingMethodModel, List<ResolvedMapping>> methodMappings;
    Map<MappingMethodModel, Set<String>> unmappedTargets;
    Map<MappingMethodModel, Map<String, Set<String>>> duplicateTargets;
}
