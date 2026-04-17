package io.github.joke.percolate.processor.spi;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.MapOptKey;
import io.github.joke.percolate.processor.transform.CodeTemplate;
import io.github.joke.percolate.processor.transform.TransformProposal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeTransformStrategy.class)
public final class StringToTemporalStrategy implements TypeTransformStrategy {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "java.time.LocalDate",
            "java.time.LocalTime",
            "java.time.LocalDateTime",
            "java.time.Instant",
            "java.time.ZonedDateTime",
            "java.time.OffsetDateTime",
            "java.time.OffsetTime",
            "java.time.Duration",
            "java.time.Period");

    // Types that need zone bridging when using a custom format pattern
    private static final Set<String> ZONE_BRIDGED_TYPES = Set.of("java.time.Instant");

    @Override
    public Optional<TransformProposal> canProduce(
            final TypeMirror sourceType, final TypeMirror targetType, final ResolutionContext ctx) {
        if (sourceType.getKind() != TypeKind.DECLARED || targetType.getKind() != TypeKind.DECLARED) {
            return Optional.empty();
        }

        final var stringType = ctx.getElements().getTypeElement("java.lang.String");
        if (stringType == null || !ctx.getTypes().isSameType(sourceType, stringType.asType())) {
            return Optional.empty();
        }

        final var targetTypeName = targetType.toString();
        if (!SUPPORTED_TYPES.contains(targetTypeName)) {
            return Optional.empty();
        }

        final var formatOption = ctx.getOption(MapOptKey.DATE_FORMAT);
        final CodeTemplate template;
        if (formatOption.isPresent()) {
            final var pattern = formatOption.get();
            if (ZONE_BRIDGED_TYPES.contains(targetTypeName)) {
                template = input -> CodeBlock.of(
                        "$T.parse($L, $T.ofPattern($S)).atZone($T.systemDefault()).toInstant()",
                        LocalDateTime.class,
                        input,
                        DateTimeFormatter.class,
                        pattern,
                        ZoneId.class);
            } else {
                final var targetClass = resolveClass(targetTypeName);
                template = input -> CodeBlock.of(
                        "$T.parse($L, $T.ofPattern($S))", targetClass, input, DateTimeFormatter.class, pattern);
            }
        } else {
            final var targetClass = resolveClass(targetTypeName);
            template = input -> CodeBlock.of("$T.parse($L)", targetClass, input);
        }

        return Optional.of(new TransformProposal(sourceType, targetType, template, this));
    }

    private static Class<?> resolveClass(final String typeName) {
        switch (typeName) {
            case "java.time.LocalDate":
                return java.time.LocalDate.class;
            case "java.time.LocalTime":
                return java.time.LocalTime.class;
            case "java.time.LocalDateTime":
                return java.time.LocalDateTime.class;
            case "java.time.Instant":
                return java.time.Instant.class;
            case "java.time.ZonedDateTime":
                return java.time.ZonedDateTime.class;
            case "java.time.OffsetDateTime":
                return java.time.OffsetDateTime.class;
            case "java.time.OffsetTime":
                return java.time.OffsetTime.class;
            case "java.time.Duration":
                return java.time.Duration.class;
            case "java.time.Period":
                return java.time.Period.class;
            default:
                throw new IllegalArgumentException("Unsupported temporal type: " + typeName);
        }
    }
}
