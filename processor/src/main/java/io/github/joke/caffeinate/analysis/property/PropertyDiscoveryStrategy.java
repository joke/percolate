package io.github.joke.caffeinate.analysis.property;

import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

public interface PropertyDiscoveryStrategy {
    List<Property> discover(TypeElement type, ProcessingEnvironment env);
}
