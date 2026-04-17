package io.github.joke.percolate.processor;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.model.MappingMethodModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.element.TypeElement;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorMessages {

    private static final int MAX_DISPLAYED_PROPERTIES = 10;
    private static final int MAX_SUGGESTIONS = 3;
    private static final int MAX_DISTANCE = 4;

    public static String unresolvedTransform(
            final String sourceName,
            final String targetName,
            final javax.lang.model.type.TypeMirror sourceType,
            final javax.lang.model.type.TypeMirror targetType,
            final MappingMethodModel method,
            final TypeElement mapperType) {
        return unresolvedTransform(sourceName, targetName, sourceType, targetType, method, mapperType, "");
    }

    public static String unresolvedTransform(
            final String sourceName,
            final String targetName,
            final javax.lang.model.type.TypeMirror sourceType,
            final javax.lang.model.type.TypeMirror targetType,
            final MappingMethodModel method,
            final TypeElement mapperType,
            final String using) {
        final var sb = new StringBuilder();
        sb.append("Cannot map '")
                .append(sourceName)
                .append("' (")
                .append(sourceType)
                .append(") → '")
                .append(targetName)
                .append("' (")
                .append(targetType)
                .append(")")
                .append(" in method '")
                .append(method.getMethod())
                .append("' of ")
                .append(mapperType)
                .append(": no mapping method found for ")
                .append(sourceType)
                .append(" → ")
                .append(targetType);
        if (!using.isEmpty()) {
            sb.append(" (using = \"").append(using).append("\")");
        }
        return sb.toString();
    }

    public static String ambiguousMethodCandidates(
            final String sourceName, final String targetName, final List<String> candidateDescriptions) {
        final var sb = new StringBuilder();
        sb.append("Ambiguous mapping for '")
                .append(sourceName)
                .append("' → '")
                .append(targetName)
                .append("'.\n");
        sb.append("  Multiple methods match and none is more specific:\n");
        for (final var desc : candidateDescriptions) {
            sb.append("    - ").append(desc).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    public static String unknownSourceProperty(
            final String name, final MappingMethodModel method, final Set<String> availableNames) {
        final var sb = new StringBuilder();
        sb.append("Unknown source property '").append(name).append("'");
        sb.append(" on method '").append(method.getMethod()).append("'.\n");
        sb.append("  Source type: ").append(method.getSourceType()).append('\n');
        appendAvailableProperties(sb, "source", availableNames);
        suggest(name, availableNames)
                .ifPresent(s -> sb.append("  Did you mean: ").append(s).append("?\n"));
        return sb.toString().stripTrailing();
    }

    public static String unknownTargetProperty(
            final String name, final MappingMethodModel method, final Set<String> availableNames) {
        final var sb = new StringBuilder();
        sb.append("Unknown target property '").append(name).append("'");
        sb.append(" on method '").append(method.getMethod()).append("'.\n");
        sb.append("  Target type: ").append(method.getTargetType()).append('\n');
        appendAvailableProperties(sb, "target", availableNames);
        suggest(name, availableNames)
                .ifPresent(s -> sb.append("  Did you mean: ").append(s).append("?\n"));
        return sb.toString().stripTrailing();
    }

    public static String unmappedTargetProperty(
            final String name, final TypeElement mapperType, final Set<String> unmappedSourceNames) {
        final var sb = new StringBuilder();
        sb.append("Unmapped target property '").append(name).append("'");
        sb.append(" on ").append(mapperType).append(".\n");
        if (!unmappedSourceNames.isEmpty()) {
            final var sorted = new TreeSet<>(unmappedSourceNames);
            sb.append("  Unmapped source properties: ").append(sorted).append('\n');
            suggest(name, unmappedSourceNames).ifPresent(s -> sb.append("  Did you mean to map '")
                    .append(s)
                    .append("' -> '")
                    .append(name)
                    .append("'?\n"));
        }
        return sb.toString().stripTrailing();
    }

    public static String dateFormatOnNonStringMapping(
            final String sourceName, final String targetName, final MappingMethodModel method) {
        return "Option DATE_FORMAT on mapping '" + sourceName + "' → '" + targetName
                + "' in method '" + method.getMethod()
                + "': DATE_FORMAT requires the source or target to be java.lang.String";
    }

    public static String dateFormatOnTemporalWithoutAccessor(
            final String sourceName, final String targetName, final String typeName, final MappingMethodModel method) {
        return "Option DATE_FORMAT on mapping '" + sourceName + "' → '" + targetName
                + "' in method '" + method.getMethod()
                + "': DATE_FORMAT is not supported for " + typeName
                + " (Duration and Period cannot be formatted with DateTimeFormatter)";
    }

    public static String conflictingMappings(
            final String name, final TypeElement mapperType, final Set<String> sourceNames) {
        final var sb = new StringBuilder();
        sb.append("Conflicting mappings for target property '").append(name).append("'");
        sb.append(" on ").append(mapperType).append(".\n");
        final var sorted = new TreeSet<>(sourceNames);
        sb.append("  Mapped from: ").append(sorted);
        return sb.toString().stripTrailing();
    }

    private static void appendAvailableProperties(final StringBuilder sb, final String kind, final Set<String> names) {
        final var sorted = new ArrayList<>(new TreeSet<>(names));
        if (sorted.size() <= MAX_DISPLAYED_PROPERTIES) {
            sb.append("  Available ")
                    .append(kind)
                    .append(" properties: ")
                    .append(sorted)
                    .append('\n');
        } else {
            final var displayed = sorted.subList(0, MAX_DISPLAYED_PROPERTIES);
            final var remaining = sorted.size() - MAX_DISPLAYED_PROPERTIES;
            sb.append("  Available ")
                    .append(kind)
                    .append(" properties: ")
                    .append(displayed)
                    .append(" and ")
                    .append(remaining)
                    .append(" more\n");
        }
    }

    private static Optional<String> suggest(final String name, final Set<String> candidates) {
        final int threshold = Math.min(MAX_DISTANCE, name.length() / 2);
        final List<String> suggestions = candidates.stream()
                .filter(c -> levenshtein(name, c) <= threshold)
                .sorted((a, b) -> Integer.compare(levenshtein(name, a), levenshtein(name, b)))
                .limit(MAX_SUGGESTIONS)
                .collect(toUnmodifiableList());
        if (suggestions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(String.join(", ", suggestions));
    }

    public static int levenshtein(final String a, final String b) {
        final int m = a.length();
        final int n = b.length();
        final int[] prev = new int[n + 1];
        final int[] curr = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                final int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            System.arraycopy(curr, 0, prev, 0, n + 1);
        }

        return prev[n];
    }
}
