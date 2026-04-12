package io.github.joke.percolate.processor.spi;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.MapOptKey;
import io.github.joke.percolate.processor.transform.CodeTemplate;
import io.github.joke.percolate.processor.transform.TransformProposal;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeTransformStrategy.class)
public final class TemporalToStringStrategy implements TypeTransformStrategy {

    private static final Set<String> ZONE_REQUIRED_TYPES = Set.of(
            "java.time.Instant");

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

    @Override
    public Optional<TransformProposal> canProduce(
            final TypeMirror sourceType, final TypeMirror targetType, final ResolutionContext ctx) {
        if (sourceType.getKind() != TypeKind.DECLARED || targetType.getKind() != TypeKind.DECLARED) {
            return Optional.empty();
        }

        final var sourceTypeName = sourceType.toString();
        if (!SUPPORTED_TYPES.contains(sourceTypeName)) {
            return Optional.empty();
        }

        final var stringType = ctx.getElements().getTypeElement("java.lang.String");
        if (stringType == null || !ctx.getTypes().isSameType(targetType, stringType.asType())) {
            return Optional.empty();
        }

        final var formatOption = ctx.getOption(MapOptKey.DATE_FORMAT);
        final CodeTemplate template;
        if (formatOption.isPresent()) {
            final var pattern = formatOption.get();
            if (ZONE_REQUIRED_TYPES.contains(sourceTypeName)) {
                template = input -> CodeBlock.of(
                        "$L.atZone($T.systemDefault()).format($T.ofPattern($S))",
                        input, ZoneId.class, DateTimeFormatter.class, pattern);
            } else {
                template = input -> CodeBlock.of(
                        "$L.format($T.ofPattern($S))", input, DateTimeFormatter.class, pattern);
            }
        } else {
            template = input -> CodeBlock.of("$L.toString()", input);
        }

        return Optional.of(new TransformProposal(sourceType, targetType, template, this));
    }
}
