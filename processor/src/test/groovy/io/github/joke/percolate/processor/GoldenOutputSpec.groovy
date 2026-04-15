package io.github.joke.percolate.processor

import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static com.google.testing.compile.Compiler.javac

/**
 * Golden-output regression harness.
 *
 * On first run (when golden files are absent) the test writes the current
 * processor output to {@code src/test/resources/golden/} and passes.
 * On subsequent runs it reads those files and asserts byte-identical output,
 * acting as the regression contract described in the value-graph-refactor proposal.
 *
 * Only the fixtures enumerated here are covered; adding a new fixture also
 * requires a corresponding golden file (captured the same way).
 */
@Tag('unit')
class GoldenOutputSpec extends Specification {

    private static final Path GOLDEN_DIR = Paths.get(
            GoldenOutputSpec.class.getResource('/').toURI()
    ).resolve('../../../../src/test/resources/golden').normalize()

    def 'constructor-based mapper with explicit @Map directives matches golden'() {
        given:
        final source = JavaFileObjects.forSourceString('test.SourceBean', '''
            package test;
            public class SourceBean {
                private final String firstName;
                private final String lastName;
                public SourceBean(String firstName, String lastName) {
                    this.firstName = firstName;
                    this.lastName = lastName;
                }
                public String getFirstName() { return firstName; }
                public String getLastName() { return lastName; }
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.TargetBean', '''
            package test;
            public class TargetBean {
                private final String givenName;
                private final String familyName;
                public TargetBean(String givenName, String familyName) {
                    this.givenName = givenName;
                    this.familyName = familyName;
                }
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.PersonMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface PersonMapper {
                @Map(source = "firstName", target = "givenName")
                @Map(source = "lastName", target = "familyName")
                TargetBean map(SourceBean source);
            }
        ''')

        when:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)

        then:
        compilation.status().name() == 'SUCCESS'
        assertGolden('PersonMapperImpl', compilation)
    }

    def 'field-based mapper matches golden'() {
        given:
        final source = JavaFileObjects.forSourceString('test.FieldSource', '''
            package test;
            public class FieldSource {
                public String name;
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.FieldTarget', '''
            package test;
            public class FieldTarget {
                public String name;
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.FieldMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface FieldMapper {
                @Map(source = "name", target = "name")
                FieldTarget map(FieldSource source);
            }
        ''')

        when:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)

        then:
        compilation.status().name() == 'SUCCESS'
        assertGolden('FieldMapperImpl', compilation)
    }

    def 'auto-mapped same-name properties match golden'() {
        given:
        final source = JavaFileObjects.forSourceString('test.AutoSource', '''
            package test;
            public class AutoSource {
                private final String name;
                private final int age;
                public AutoSource(String name, int age) {
                    this.name = name;
                    this.age = age;
                }
                public String getName() { return name; }
                public int getAge() { return age; }
            }
        ''')
        final target = JavaFileObjects.forSourceString('test.AutoTarget', '''
            package test;
            public class AutoTarget {
                private final String name;
                private final int age;
                public AutoTarget(String name, int age) {
                    this.name = name;
                    this.age = age;
                }
            }
        ''')
        final mapper = JavaFileObjects.forSourceString('test.AutoMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;

            @Mapper
            public interface AutoMapper {
                AutoTarget map(AutoSource source);
            }
        ''')

        when:
        final compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)

        then:
        compilation.status().name() == 'SUCCESS'
        assertGolden('AutoMapperImpl', compilation)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void assertGolden(final String implName, final def compilation) {
        final generatedFile = compilation.generatedSourceFiles()
                .find { it.name.contains(implName) }
        assert generatedFile != null, "No generated file containing '$implName'"

        final actualContent = generatedFile.getCharContent(true).toString()
        final goldenFile = GOLDEN_DIR.resolve("${implName}.java")

        if (!Files.exists(goldenFile)) {
            // Capture mode: write golden on first run.
            Files.createDirectories(goldenFile.parent)
            Files.writeString(goldenFile, actualContent)
            // Pass on capture — the developer must commit the golden file.
            return
        }

        final expectedContent = Files.readString(goldenFile)
        assert actualContent == expectedContent :
                "Generated output for '$implName' does not match golden.\n" +
                "If this change is intentional, delete the golden file and re-run to recapture.\n" +
                "Golden: ${goldenFile}"
    }
}
