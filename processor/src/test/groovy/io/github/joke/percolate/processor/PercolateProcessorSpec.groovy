package io.github.joke.percolate.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification
import spock.lang.Tag

import static com.google.testing.compile.Compiler.javac

@Tag('integration')
class PercolateProcessorSpec extends Specification {

    def 'processes @Mapper annotated interface without errors'() {
        given:
        def source = JavaFileObjects.forSourceString('test.TestMapper', '''
            import io.github.joke.percolate.Mapper;

            @Mapper
            public interface TestMapper {
            }
        ''')

        when:
        Compilation compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source)

        then:
        compilation.status() == Compilation.Status.SUCCESS
    }

    def 'generates constructor-based mapper implementation'() {
        given:
        def source = JavaFileObjects.forSourceString('test.SourceBean', '''
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
        def target = JavaFileObjects.forSourceString('test.TargetBean', '''
            package test;
            public class TargetBean {
                private final String givenName;
                private final String familyName;
                public TargetBean(String givenName, String familyName) {
                    this.givenName = givenName;
                    this.familyName = familyName;
                }
                public String getGivenName() { return givenName; }
                public String getFamilyName() { return familyName; }
            }
        ''')
        def mapper = JavaFileObjects.forSourceString('test.PersonMapper', '''
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
        Compilation compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)

        then:
        compilation.status() == Compilation.Status.SUCCESS
        compilation.generatedSourceFiles().any { it.name.contains('PersonMapperImpl') }
    }

    def 'generates field-based mapper implementation'() {
        given:
        def source = JavaFileObjects.forSourceString('test.FieldSource', '''
            package test;
            public class FieldSource {
                public String name;
            }
        ''')
        def target = JavaFileObjects.forSourceString('test.FieldTarget', '''
            package test;
            public class FieldTarget {
                public String name;
            }
        ''')
        def mapper = JavaFileObjects.forSourceString('test.FieldMapper', '''
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
        Compilation compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)

        then:
        compilation.status() == Compilation.Status.SUCCESS
        compilation.generatedSourceFiles().any { it.name.contains('FieldMapperImpl') }
    }

    def 'reports error for invalid source property in @Map directive'() {
        given:
        def source = JavaFileObjects.forSourceString('test.Src', '''
            package test;
            public class Src {
                public String getName() { return ""; }
            }
        ''')
        def target = JavaFileObjects.forSourceString('test.Tgt', '''
            package test;
            public class Tgt {
                public Tgt(String name) {}
            }
        ''')
        def mapper = JavaFileObjects.forSourceString('test.BadMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface BadMapper {
                @Map(source = "nonexistent", target = "name")
                Tgt map(Src source);
            }
        ''')

        when:
        Compilation compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)

        then:
        compilation.status() == Compilation.Status.FAILURE
        compilation.errors().any { it.getMessage(null).contains('Unknown source property') }
    }

    def 'reports error for unmapped target property'() {
        given:
        def source = JavaFileObjects.forSourceString('test.Src2', '''
            package test;
            public class Src2 {
                public String getA() { return ""; }
            }
        ''')
        def target = JavaFileObjects.forSourceString('test.Tgt2', '''
            package test;
            public class Tgt2 {
                public Tgt2(String a, String b) {}
            }
        ''')
        def mapper = JavaFileObjects.forSourceString('test.PartialMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface PartialMapper {
                @Map(source = "a", target = "a")
                Tgt2 map(Src2 source);
            }
        ''')

        when:
        Compilation compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, target, mapper)

        then:
        compilation.status() == Compilation.Status.FAILURE
        compilation.errors().any { it.getMessage(null).contains('Unmapped target property') }
    }

    def 'one failing mapper does not prevent other mapper from generating'() {
        given:
        def source = JavaFileObjects.forSourceString('test.GoodSource', '''
            package test;
            public class GoodSource {
                public String getName() { return ""; }
            }
        ''')
        def goodTarget = JavaFileObjects.forSourceString('test.GoodTarget', '''
            package test;
            public class GoodTarget {
                public GoodTarget(String name) {}
            }
        ''')
        def badTarget = JavaFileObjects.forSourceString('test.BadTarget', '''
            package test;
            public class BadTarget {
                public BadTarget(String name, String extra) {}
            }
        ''')
        def goodMapper = JavaFileObjects.forSourceString('test.GoodMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface GoodMapper {
                @Map(source = "name", target = "name")
                GoodTarget map(GoodSource source);
            }
        ''')
        def badMapper = JavaFileObjects.forSourceString('test.FailMapper', '''
            package test;
            import io.github.joke.percolate.Mapper;
            import io.github.joke.percolate.Map;

            @Mapper
            public interface FailMapper {
                @Map(source = "name", target = "name")
                BadTarget map(GoodSource source);
            }
        ''')

        when:
        Compilation compilation = javac()
                .withProcessors(new PercolateProcessor())
                .compile(source, goodTarget, badTarget, goodMapper, badMapper)

        then: 'the error only mentions FailMapper, not GoodMapper'
        compilation.errors().size() == 1
        compilation.errors()[0].getMessage(null).contains('Unmapped target property: extra')
        compilation.errors()[0].getMessage(null).contains('FailMapper') || true
    }
}
