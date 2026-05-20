package io.github.joke.percolate.processor.graph;

import static java.util.stream.Collectors.toList;

final class DotGroupClusterRenderer {

    private DotGroupClusterRenderer() {}

    static void render(final StringBuilder sb, final MapperGraph graph) {
        final var groupList = graph.groups().collect(toList());
        for (var idx = 0; idx < groupList.size(); idx++) {
            renderOne(sb, groupList.get(idx), idx);
        }
    }

    private static void renderOne(final StringBuilder sb, final ExpansionGroup group, final int index) {
        final var clusterId = "group_" + index;
        final var label = simpleClassName(group.getStrategyClassFqn()) + " #" + index;
        sb.append("  subgraph \"cluster_")
                .append(DotRenderer.escapeDot(clusterId))
                .append("\" {\n    label=\"")
                .append(DotRenderer.escapeDot(label))
                .append("\";\n    style=\"dashed\";\n");
        appendNodeRef(sb, group.getRoot().id());
        for (final var slot : group.getSlots()) {
            appendNodeRef(sb, slot.id());
        }
        sb.append("  }\n");
    }

    private static void appendNodeRef(final StringBuilder sb, final String nodeId) {
        sb.append("    \"").append(DotRenderer.escapeDot(nodeId)).append("\";\n");
    }

    private static String simpleClassName(final String fqn) {
        final var lastDot = fqn.lastIndexOf('.');
        if (lastDot >= 0) {
            return fqn.substring(lastDot + 1);
        }
        return fqn;
    }
}
