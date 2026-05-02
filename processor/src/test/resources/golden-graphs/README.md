# Golden DOT Graphs

Golden DOT files guard the rendering pipeline. They are checked into version control
and reviewed in PRs like code.

## Regenerating Goldens

To regenerate all golden files, run:

```bash
./gradlew :processor:processTestResources -PupdateGoldens=true
```

Or run the `updateGoldens` Gradle task directly:

```bash
./gradlew :processor:updateGoldens
```

This task compiles fixture mappers with `-Apercolate.debug.graphs=true` and writes
the generated `.seed.dot` files to this directory.

## Reviewing Changes

When regenerating goldens, review the diff carefully. Changes to golden files indicate
changes to the DOT renderer output. Common reasons for changes:

- DOT cluster name quoting (double quotes around cluster names)
- Node shape changes (box for source, oval for target)
- Graph structure changes (new nodes/edges from SeedGraph updates)
- Attribute ordering changes (alphabetical within statements)
