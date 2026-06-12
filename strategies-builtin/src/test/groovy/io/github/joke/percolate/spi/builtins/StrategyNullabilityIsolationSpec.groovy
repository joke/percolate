package io.github.joke.percolate.spi.builtins

import spock.lang.Specification
import spock.lang.Tag

import java.nio.file.Files
import java.util.stream.Collectors

/**
 * Architectural fitness check for the {@code nullability} capability: strategy and SPI code is forbidden from
 * reasoning about nullability — the engine performs every {@code NullabilityResolver.resolve(...)} call. This pins
 * the spec scenario "No strategy class calls NullabilityResolver" so the contract cannot silently regress as new
 * strategies are added.
 */
@Tag('unit')
class StrategyNullabilityIsolationSpec extends Specification {

    def 'no main source under spi or strategies-builtin references NullabilityResolver'() {
        given:
        def root = repoRoot()
        def sourceRoots = [
                new File(root, 'spi/src/main/java'),
                new File(root, 'strategies-builtin/src/main/java'),
        ]

        expect: 'the source roots resolve (fail loudly if the module layout changes)'
        sourceRoots.every { it.directory }

        when:
        def offenders = sourceRoots.collectMany { javaFiles(it) }
                .findAll { it.text.contains('NullabilityResolver') }*.name

        then:
        offenders.empty
    }

    private static List<File> javaFiles(final File dir) {
        Files.walk(dir.toPath())
                .filter { it.toString().endsWith('.java') }
                .map { it.toFile() }
                .collect(Collectors.toList())
    }

    /** The repository root: the nearest ancestor of the working directory that holds {@code settings.gradle}. */
    private static File repoRoot() {
        def dir = new File('.').absoluteFile
        while (dir != null && !new File(dir, 'settings.gradle').file) {
            dir = dir.parentFile
        }
        assert dir != null: 'could not locate the repository root (no settings.gradle above the working directory)'
        dir
    }
}
