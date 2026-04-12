package io.github.joke.percolate.processor.spi;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.transform.TransformProposal;
import java.util.Date;
import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

@AutoService(TypeTransformStrategy.class)
public final class LegacyDateBridgeStrategy implements TypeTransformStrategy {

    private static final String JAVA_UTIL_DATE = "java.util.Date";
    private static final String JAVA_SQL_DATE = "java.sql.Date";
    private static final String JAVA_SQL_TIME = "java.sql.Time";
    private static final String JAVA_SQL_TIMESTAMP = "java.sql.Timestamp";
    private static final String JAVA_TIME_INSTANT = "java.time.Instant";
    private static final String JAVA_TIME_LOCAL_DATE = "java.time.LocalDate";
    private static final String JAVA_TIME_LOCAL_TIME = "java.time.LocalTime";

    @Override
    public Optional<TransformProposal> canProduce(
            final TypeMirror sourceType, final TypeMirror targetType, final ResolutionContext ctx) {
        if (sourceType.getKind() != TypeKind.DECLARED || targetType.getKind() != TypeKind.DECLARED) {
            return Optional.empty();
        }

        final var src = sourceType.toString();
        final var tgt = targetType.toString();

        // Legacy → modern: always return a proposal that introduces the modern intermediate type
        // into the BFS graph, regardless of what targetType is. This enables multi-hop paths
        // like java.util.Date → Instant → String to be discovered automatically.
        if (JAVA_UTIL_DATE.equals(src)) {
            final var instantType = resolveType(JAVA_TIME_INSTANT, ctx);
            if (instantType == null) return Optional.empty();
            return Optional.of(new TransformProposal(
                    sourceType, instantType,
                    input -> CodeBlock.of("$L.toInstant()", input),
                    this));
        }
        if (JAVA_SQL_DATE.equals(src)) {
            final var localDateType = resolveType(JAVA_TIME_LOCAL_DATE, ctx);
            if (localDateType == null) return Optional.empty();
            return Optional.of(new TransformProposal(
                    sourceType, localDateType,
                    input -> CodeBlock.of("$L.toLocalDate()", input),
                    this));
        }
        if (JAVA_SQL_TIME.equals(src)) {
            final var localTimeType = resolveType(JAVA_TIME_LOCAL_TIME, ctx);
            if (localTimeType == null) return Optional.empty();
            return Optional.of(new TransformProposal(
                    sourceType, localTimeType,
                    input -> CodeBlock.of("$L.toLocalTime()", input),
                    this));
        }
        if (JAVA_SQL_TIMESTAMP.equals(src)) {
            final var instantType = resolveType(JAVA_TIME_INSTANT, ctx);
            if (instantType == null) return Optional.empty();
            return Optional.of(new TransformProposal(
                    sourceType, instantType,
                    input -> CodeBlock.of("$L.toInstant()", input),
                    this));
        }

        // Modern → legacy: strict pair matching to avoid speculatively adding legacy type nodes.
        if (JAVA_TIME_INSTANT.equals(src) && JAVA_UTIL_DATE.equals(tgt)) {
            return Optional.of(new TransformProposal(
                    sourceType, targetType,
                    input -> CodeBlock.of("$T.from($L)", Date.class, input),
                    this));
        }
        if (JAVA_TIME_LOCAL_DATE.equals(src) && JAVA_SQL_DATE.equals(tgt)) {
            return Optional.of(new TransformProposal(
                    sourceType, targetType,
                    input -> CodeBlock.of("$T.valueOf($L)", java.sql.Date.class, input),
                    this));
        }
        if (JAVA_TIME_LOCAL_TIME.equals(src) && JAVA_SQL_TIME.equals(tgt)) {
            return Optional.of(new TransformProposal(
                    sourceType, targetType,
                    input -> CodeBlock.of("$T.valueOf($L)", java.sql.Time.class, input),
                    this));
        }
        if (JAVA_TIME_INSTANT.equals(src) && JAVA_SQL_TIMESTAMP.equals(tgt)) {
            return Optional.of(new TransformProposal(
                    sourceType, targetType,
                    input -> CodeBlock.of("$T.from($L)", java.sql.Timestamp.class, input),
                    this));
        }

        return Optional.empty();
    }

    private static @Nullable TypeMirror resolveType(final String typeName, final ResolutionContext ctx) {
        final TypeElement element = ctx.getElements().getTypeElement(typeName);
        if (element == null) {
            return null;
        }
        return ctx.getTypes().getDeclaredType(element);
    }
}
