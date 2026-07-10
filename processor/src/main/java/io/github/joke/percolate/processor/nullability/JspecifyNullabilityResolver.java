package io.github.joke.percolate.processor.nullability;

import io.github.joke.percolate.spi.Nullability;
import jakarta.inject.Inject;
import java.util.Set;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class JspecifyNullabilityResolver implements NullabilityResolver {

    private final NullabilityAnnotations annotations;
    private final Elements elements;

    @Override
    public Nullability resolve(final TypeMirror type, final Element scope) {
        if (hasAny(type, annotations.getNullableFqns())) {
            return Nullability.NULLABLE;
        }
        final var fromEnclosing = markedNullabilityOfEnclosing(scope);
        if (fromEnclosing != null) {
            return fromEnclosing;
        }
        final var fromPackage = markedNullabilityOfPackage(scope);
        return fromPackage != null ? fromPackage : Nullability.UNKNOWN;
    }

    private @Nullable Nullability markedNullabilityOfEnclosing(final Element scope) {
        for (Element current = scope; current != null; current = current.getEnclosingElement()) {
            final var marked = markedNullability(current);
            if (marked != null) {
                return marked;
            }
        }
        return null;
    }

    private @Nullable Nullability markedNullabilityOfPackage(final Element scope) {
        final var pkg = elements.getPackageOf(scope);
        return pkg == null ? null : markedNullability(pkg);
    }

    private @Nullable Nullability markedNullability(final AnnotatedConstruct construct) {
        if (hasAny(construct, annotations.getUnmarkedFqns())) {
            return Nullability.UNKNOWN;
        }
        return hasAny(construct, annotations.getMarkedFqns()) ? Nullability.NON_NULL : null;
    }

    private static boolean hasAny(final AnnotatedConstruct construct, final Set<String> fqns) {
        for (final AnnotationMirror mirror : construct.getAnnotationMirrors()) {
            final String fqn = annotationFqn(mirror);
            if (fqn != null && fqns.contains(fqn)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable String annotationFqn(final AnnotationMirror mirror) {
        final DeclaredType annotationType = mirror.getAnnotationType();
        final Element annotationElement = annotationType.asElement();
        if (annotationElement instanceof TypeElement) {
            return ((TypeElement) annotationElement).getQualifiedName().toString();
        }
        return null;
    }
}
