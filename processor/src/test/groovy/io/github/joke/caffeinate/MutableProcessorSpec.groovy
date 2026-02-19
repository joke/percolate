package io.github.joke.caffeinate

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification

import static com.google.testing.compile.Compiler.javac

class MutableProcessorSpec extends Specification {

    def 'generates mutable implementation for @Mutable interface with single getter'() {
        given:
        def source = JavaFileObjects.forSourceString('test.Person', '''\
            package test;
            import io.github.joke.caffeinate.Mutable;
            @Mutable
            public interface Person {
                String getFirstName();
            }
        ''')

        when:
        def compilation = javac()
            .withProcessors(new CaffeinateProcessor())
            .compile(source)

        then:
        compilation.status() == Compilation.Status.SUCCESS

        and:
        def generated = compilation.generatedSourceFile('test.PersonImpl')
            .get().getCharContent(true).toString()
        generated.contains('public class PersonImpl implements Person')
        generated.contains('private String firstName')
        !generated.contains('private final String firstName')
        generated.contains('public PersonImpl()')
        generated.contains('public PersonImpl(String firstName)')
        generated.contains('this.firstName = firstName')
        generated.contains('public String getFirstName()')
        generated.contains('return this.firstName')
        generated.contains('public void setFirstName(String firstName)')
    }

    def 'generates mutable fields and both constructors for multiple getters'() {
        given:
        def source = JavaFileObjects.forSourceString('test.Person', '''\
            package test;
            import io.github.joke.caffeinate.Mutable;
            @Mutable
            public interface Person {
                String getFirstName();
                int getAge();
            }
        ''')

        when:
        def compilation = javac()
            .withProcessors(new CaffeinateProcessor())
            .compile(source)

        then:
        compilation.status() == Compilation.Status.SUCCESS

        and:
        def generated = compilation.generatedSourceFile('test.PersonImpl')
            .get().getCharContent(true).toString()
        generated.contains('private String firstName')
        generated.contains('private int age')
        !generated.contains('private final')
        generated.contains('public PersonImpl()')
        generated.contains('PersonImpl(String firstName, int age)')
        generated.contains('public void setFirstName(String firstName)')
        generated.contains('public void setAge(int age)')
    }

    def 'generates setter for boolean is* getter'() {
        given:
        def source = JavaFileObjects.forSourceString('test.Status', '''\
            package test;
            import io.github.joke.caffeinate.Mutable;
            @Mutable
            public interface Status {
                boolean isActive();
            }
        ''')

        when:
        def compilation = javac()
            .withProcessors(new CaffeinateProcessor())
            .compile(source)

        then:
        compilation.status() == Compilation.Status.SUCCESS

        and:
        def generated = compilation.generatedSourceFile('test.StatusImpl')
            .get().getCharContent(true).toString()
        generated.contains('private boolean active')
        generated.contains('public boolean isActive()')
        generated.contains('public void setActive(boolean active)')
    }

    def 'succeeds when interface declares matching setter'() {
        given:
        def source = JavaFileObjects.forSourceString('test.Person', '''\
            package test;
            import io.github.joke.caffeinate.Mutable;
            @Mutable
            public interface Person {
                String getFirstName();
                void setFirstName(String firstName);
            }
        ''')

        when:
        def compilation = javac()
            .withProcessors(new CaffeinateProcessor())
            .compile(source)

        then:
        compilation.status() == Compilation.Status.SUCCESS

        and:
        def generated = compilation.generatedSourceFile('test.PersonImpl')
            .get().getCharContent(true).toString()
        generated.contains('public void setFirstName(String firstName)')
    }

    def 'generates only no-args constructor for empty interface'() {
        given:
        def source = JavaFileObjects.forSourceString('test.Empty', '''\
            package test;
            import io.github.joke.caffeinate.Mutable;
            @Mutable
            public interface Empty {}
        ''')

        when:
        def compilation = javac()
            .withProcessors(new CaffeinateProcessor())
            .compile(source)

        then:
        compilation.status() == Compilation.Status.SUCCESS

        and:
        def generated = compilation.generatedSourceFile('test.EmptyImpl')
            .get().getCharContent(true).toString()
        generated.contains('public class EmptyImpl implements Empty')
        generated.contains('public EmptyImpl()')
    }

    def 'fails when @Mutable applied to a class'() {
        given:
        def source = JavaFileObjects.forSourceString('test.Person', '''\
            package test;
            import io.github.joke.caffeinate.Mutable;
            @Mutable
            public class Person {}
        ''')

        when:
        def compilation = javac()
            .withProcessors(new CaffeinateProcessor())
            .compile(source)

        then:
        compilation.status() == Compilation.Status.FAILURE
        compilation.errors().any {
            it.getMessage(null).contains('@Mutable can only be applied to interfaces')
        }
    }

    def 'fails when method name does not follow naming convention'() {
        given:
        def source = JavaFileObjects.forSourceString('test.Person', '''\
            package test;
            import io.github.joke.caffeinate.Mutable;
            @Mutable
            public interface Person {
                String firstName();
            }
        ''')

        when:
        def compilation = javac()
            .withProcessors(new CaffeinateProcessor())
            .compile(source)

        then:
        compilation.status() == Compilation.Status.FAILURE
        compilation.errors().any {
            it.getMessage(null).contains('must follow get*/is*/set* naming convention')
        }
    }

    def 'fails when declared setter does not match any property'() {
        given:
        def source = JavaFileObjects.forSourceString('test.Person', '''\
            package test;
            import io.github.joke.caffeinate.Mutable;
            @Mutable
            public interface Person {
                String getFirstName();
                void setLastName(String lastName);
            }
        ''')

        when:
        def compilation = javac()
            .withProcessors(new CaffeinateProcessor())
            .compile(source)

        then:
        compilation.status() == Compilation.Status.FAILURE
        compilation.errors().any {
            it.getMessage(null).contains('does not match any getter-derived property')
        }
    }

    def 'fails when declared setter has wrong parameter type'() {
        given:
        def source = JavaFileObjects.forSourceString('test.Person', '''\
            package test;
            import io.github.joke.caffeinate.Mutable;
            @Mutable
            public interface Person {
                String getFirstName();
                void setFirstName(int wrongType);
            }
        ''')

        when:
        def compilation = javac()
            .withProcessors(new CaffeinateProcessor())
            .compile(source)

        then:
        compilation.status() == Compilation.Status.FAILURE
        compilation.errors().any {
            it.getMessage(null).contains('does not match any getter-derived property')
        }
    }

    def 'generates field and setter for getter inherited from parent interface'() {
        given:
        def named = JavaFileObjects.forSourceString('test.Named', '''\
            package test;
            interface Named { String getName(); }
        ''')
        def source = JavaFileObjects.forSourceString('test.Person', '''\
            package test;
            import io.github.joke.caffeinate.Mutable;
            @Mutable
            public interface Person extends Named {
                int getAge();
            }
        ''')

        when:
        def compilation = javac()
            .withProcessors(new CaffeinateProcessor())
            .compile(named, source)

        then:
        compilation.status() == Compilation.Status.SUCCESS

        and:
        def generated = compilation.generatedSourceFile('test.PersonImpl')
            .get().getCharContent(true).toString()
        generated.contains('private String name')
        generated.contains('private int age')
        !generated.contains('private final String name')
        !generated.contains('private final int age')
        generated.contains('public String getName()')
        generated.contains('public void setName(String name)')
        generated.contains('public int getAge()')
        generated.contains('public void setAge(int age)')
    }
}
