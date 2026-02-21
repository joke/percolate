package io.github.joke.caffeinate.analysis.property;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.List;

public interface PropertyDiscoveryStrategy {
    List<Property> discover(TypeElement type, ProcessingEnvironment env);
}
