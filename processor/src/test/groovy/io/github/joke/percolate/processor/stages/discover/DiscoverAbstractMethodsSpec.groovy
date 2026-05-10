package io.github.joke.percolate.processor.stages.discover


import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement

/**
 * Unit tests for DiscoverAbstractMethods using mocked Elements/Types.
 * <p>
 * GAP: The following scenarios from task 4.4 / mapper-discovery spec are NOT covered here
 * because they require compile-testing-backed integration tests:
 * <ul>
 *   <li>Inherited abstract methods from super-interfaces (with generic substitution)</li>
 *   <li>Abstract super-class contributes inherited abstract methods</li>
 *   <li>Default method on sub-interface implements abstract from parent (skipped correctly)</li>
 *   <li>Static interface methods are skipped</li>
 *   <li>Private interface methods are skipped</li>
 * </ul>
 * These scenarios should be covered by {@code @Tag('integration')} tests using Google Compile Testing
 * (see design.md D5 and task 4.4).
 */

@Tag('unit')
class DiscoverAbstractMethodsSpec extends Specification {

    def 'local abstract method is discovered'() {
        given:
        def stage = new DiscoverAbstractMethods(null, null)
        def typeElement = Mock(TypeElement)
        def objectElement = Mock(TypeElement)
        def method = Mock(ExecutableElement)
        method.getModifiers() >> [Modifier.ABSTRACT]
        method.getEnclosingElement() >> Mock(TypeElement)

        when:
        def shape = stage.filter(typeElement, [method], objectElement)

        then:
        shape.abstractMethods == [method]
    }

    def 'multiple methods are discovered in declaration order'() {
        given:
        def stage = new DiscoverAbstractMethods(null, null)
        def typeElement = Mock(TypeElement)
        def objectElement = Mock(TypeElement)
        def methodA = Mock(ExecutableElement)
        def methodB = Mock(ExecutableElement)
        methodA.getModifiers() >> [Modifier.ABSTRACT]
        methodB.getModifiers() >> [Modifier.ABSTRACT]
        methodA.getEnclosingElement() >> Mock(TypeElement)
        methodB.getEnclosingElement() >> Mock(TypeElement)

        when:
        def shape = stage.filter(typeElement, [methodA, methodB], objectElement)

        then:
        shape.abstractMethods == [methodA, methodB]
    }

    def 'default method is skipped'() {
        given:
        def stage = new DiscoverAbstractMethods(null, null)
        def typeElement = Mock(TypeElement)
        def objectElement = Mock(TypeElement)
        def defaultMethod = Mock(ExecutableElement)
        defaultMethod.getModifiers() >> [Modifier.PUBLIC, Modifier.DEFAULT]

        when:
        def shape = stage.filter(typeElement, [defaultMethod], objectElement)

        then:
        shape.abstractMethods.isEmpty()
    }

    def 'concrete method on abstract class is skipped'() {
        given:
        def stage = new DiscoverAbstractMethods(null, null)
        def typeElement = Mock(TypeElement)
        def objectElement = Mock(TypeElement)
        def concreteMethod = Mock(ExecutableElement)
        concreteMethod.getModifiers() >> [Modifier.PUBLIC]

        when:
        def shape = stage.filter(typeElement, [concreteMethod], objectElement)

        then:
        shape.abstractMethods.isEmpty()
    }

    def 'Object methods are skipped'() {
        given:
        def stage = new DiscoverAbstractMethods(null, null)
        def typeElement = Mock(TypeElement)
        def objectElement = Mock(TypeElement)
        def method = Mock(ExecutableElement)
        method.getModifiers() >> [Modifier.ABSTRACT, Modifier.PUBLIC]
        method.getEnclosingElement() >> objectElement

        when:
        def shape = stage.filter(typeElement, [method], objectElement)

        then:
        shape.abstractMethods.isEmpty()
    }

    def 'method with null enclosing element is not an Object method'() {
        given:
        def stage = new DiscoverAbstractMethods(null, null)
        def typeElement = Mock(TypeElement)
        def objectElement = Mock(TypeElement)
        def method = Mock(ExecutableElement)
        method.getModifiers() >> [Modifier.ABSTRACT, Modifier.PUBLIC]
        method.getEnclosingElement() >> null

        when:
        def shape = stage.filter(typeElement, [method], objectElement)

        then:
        shape.abstractMethods == [method]
    }

    def 'returns MapperShape with correct type element'() {
        given:
        def stage = new DiscoverAbstractMethods(null, null)
        def typeElement = Mock(TypeElement)
        def objectElement = Mock(TypeElement)

        when:
        def shape = stage.filter(typeElement, [], objectElement)

        then:
        shape.type == typeElement
    }
}
