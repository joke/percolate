package io.github.joke.percolate.processor.stages.discover

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.spi.ThisReceiver
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.*
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class DiscoverCallableMethodsSpec extends Specification {

    def 'locally declared single-param method is discovered'() {
        given:
        def typeElement = mockTypeElement('DogMapper')
        def method = mockMethod('map', 'Human', 'Person')
        def elements = Mock(Elements)
        elements.getAllMembers(typeElement) >> [method]
        def types = Mock(Types)
        types.isAssignable(_, _) >> { args -> args[0].toString() == args[1].toString() }
        def stage = new DiscoverCallableMethods(elements, types)
        def ctx = new MapperContext(typeElement)

        when:
        stage.run(ctx)

        then:
        def callableMethods = ctx.getCallableMethods()
        def results = callableMethods.producing(mockTypeMirror('Human')).toList()
        results.size() == 1
        results[0].method.getSimpleName().toString() == 'map'
    }

    def 'inherited method from super-interface is discovered'() {
        given:
        def typeElement = mockTypeElement('DogMapper')
        def inheritedMethod = mockMethod('adopt', 'Pet', 'Dog')
        def localMethod = mockMethod('map', 'Human', 'Person')
        def elements = Mock(Elements)
        elements.getAllMembers(typeElement) >> [inheritedMethod, localMethod]
        def types = Mock(Types)
        types.isAssignable(_, _) >> { args -> args[0].toString() == args[1].toString() }
        def stage = new DiscoverCallableMethods(elements, types)
        def ctx = new MapperContext(typeElement)

        when:
        stage.run(ctx)

        then:
        def callableMethods = ctx.getCallableMethods()
        def petResults = callableMethods.producing(mockTypeMirror('Pet')).toList()
        petResults.size() == 1
        petResults[0].method.getSimpleName().toString() == 'adopt'
    }

    def 'methods on parameter types are NOT discovered'() {
        given:
        def typeElement = mockTypeElement('DogMapper')
        def mapperMap = mockMethod('map', 'Human', 'Person')
        def elements = Mock(Elements)
        elements.getAllMembers(typeElement) >> [mapperMap]
        def types = Mock(Types)
        types.isAssignable(_, _) >> { args -> args[0].toString() == args[1].toString() }
        def stage = new DiscoverCallableMethods(elements, types)
        def ctx = new MapperContext(typeElement)

        when:
        stage.run(ctx)

        then:
        def callableMethods = ctx.getCallableMethods()
        def results = callableMethods.producing(mockTypeMirror('String')).toList()
        results.isEmpty()
    }

    def 'Object-inherited toString is excluded'() {
        given:
        def typeElement = mockTypeElement('DogMapper')
        def objectElement = Mock(TypeElement)
        objectElement.qualifiedName >> mockName('java.lang.Object')
        def objectToString = mockMethod('toString', 'String')
        objectToString.enclosingElement >> objectElement
        def mapperMethod = mockMethod('map', 'Human', 'Person')
        mapperMethod.enclosingElement >> typeElement
        def elements = Mock(Elements)
        elements.getAllMembers(typeElement) >> [objectToString, mapperMethod]
        def types = Mock(Types)
        types.isAssignable(_, _) >> { args -> args[0].toString() == args[1].toString() }
        def stage = new DiscoverCallableMethods(elements, types)
        def ctx = new MapperContext(typeElement)

        when:
        stage.run(ctx)

        then:
        def callableMethods = ctx.getCallableMethods()
        // toString from Object is filtered, but map returns Human (not String)
        def results = callableMethods.producing(mockTypeMirror('String')).toList()
        results.isEmpty()
    }

    def 'user-declared toString on the @Mapper IS discovered'() {
        given:
        def typeElement = mockTypeElement('DogMapper')
        def userToString = Mock(ExecutableElement)
        userToString.simpleName >> mockName('toString')
        userToString.kind >> ElementKind.METHOD
        def param = Mock(VariableElement)
        userToString.parameters >> [param]
        def retType = mockTypeMirror('String')
        userToString.returnType >> retType
        userToString.enclosingElement >> typeElement
        def elements = Mock(Elements)
        elements.getAllMembers(typeElement) >> [userToString]
        def types = Mock(Types)
        types.isAssignable(_, _) >> { args -> args[0].toString() == args[1].toString() }
        def stage = new DiscoverCallableMethods(elements, types)
        def ctx = new MapperContext(typeElement)

        when:
        stage.run(ctx)

        then:
        def callableMethods = ctx.getCallableMethods()
        def results = callableMethods.producing(mockTypeMirror('String')).toList()
        results.size() == 1
        results[0].method.getSimpleName().toString() == 'toString'
    }

    def 'Object-inherited hashCode is excluded'() {
        given:
        def typeElement = mockTypeElement('DogMapper')
        def objectElement = Mock(TypeElement)
        objectElement.qualifiedName >> mockName('java.lang.Object')
        def objectHashCode = Mock(ExecutableElement)
        objectHashCode.simpleName >> mockName('hashCode')
        objectHashCode.kind >> ElementKind.METHOD
        objectHashCode.parameters >> []
        def intType = mockTypeMirror('int')
        objectHashCode.returnType >> intType
        objectHashCode.enclosingElement >> objectElement
        def elements = Mock(Elements)
        elements.getAllMembers(typeElement) >> [objectHashCode]
        def types = Mock(Types)
        types.isAssignable(_, _) >> { args -> args[0].toString() == args[1].toString() }
        def stage = new DiscoverCallableMethods(elements, types)
        def ctx = new MapperContext(typeElement)

        when:
        stage.run(ctx)

        then:
        def callableMethods = ctx.getCallableMethods()
        def results = callableMethods.producing(mockTypeMirror('int')).toList()
        results.isEmpty()
    }

    def 'multi-parameter method is excluded'() {
        given:
        def typeElement = mockTypeElement('DogMapper')
        def twoParamMethod = Mock(ExecutableElement)
        twoParamMethod.simpleName >> mockName('adopt')
        twoParamMethod.kind >> ElementKind.METHOD
        def param1 = Mock(VariableElement)
        def param2 = Mock(VariableElement)
        twoParamMethod.parameters >> [param1, param2]
        def returnType = mockTypeMirror('Pet')
        twoParamMethod.returnType >> returnType
        twoParamMethod.enclosingElement >> typeElement
        def elements = Mock(Elements)
        elements.getAllMembers(typeElement) >> [twoParamMethod]
        def types = Mock(Types)
        types.isAssignable(_, _) >> { args -> args[0].toString() == args[1].toString() }
        def stage = new DiscoverCallableMethods(elements, types)
        def ctx = new MapperContext(typeElement)

        when:
        stage.run(ctx)

        then:
        def callableMethods = ctx.getCallableMethods()
        def results = callableMethods.producing(mockTypeMirror('Pet')).toList()
        results.isEmpty()
    }

    def 'single-parameter method is included'() {
        given:
        def typeElement = mockTypeElement('DogMapper')
        def oneParamMethod = Mock(ExecutableElement)
        oneParamMethod.simpleName >> mockName('adopt')
        oneParamMethod.kind >> ElementKind.METHOD
        def param = Mock(VariableElement)
        oneParamMethod.parameters >> [param]
        def returnType = mockTypeMirror('Pet')
        oneParamMethod.returnType >> returnType
        oneParamMethod.enclosingElement >> typeElement
        def elements = Mock(Elements)
        elements.getAllMembers(typeElement) >> [oneParamMethod]
        def types = Mock(Types)
        types.isAssignable(_, _) >> { args -> args[0].toString() == args[1].toString() }
        def stage = new DiscoverCallableMethods(elements, types)
        def ctx = new MapperContext(typeElement)

        when:
        stage.run(ctx)

        then:
        def callableMethods = ctx.getCallableMethods()
        def results = callableMethods.producing(mockTypeMirror('Pet')).toList()
        results.size() == 1
        results[0].method.getSimpleName().toString() == 'adopt'
    }

    def 'producing returns a candidate whose return type matches the queried type'() {
        given:
        def typeElement = mockTypeElement('DogMapper')
        def method = mockMethod('adopt', 'Pet', 'Dog')
        def elements = Mock(Elements)
        elements.getAllMembers(typeElement) >> [method]
        def types = Mock(Types)
        types.isAssignable(_, _) >> { args -> args[0].toString() == args[1].toString() }
        def stage = new DiscoverCallableMethods(elements, types)
        def ctx = new MapperContext(typeElement)

        when:
        stage.run(ctx)

        then:
        def callableMethods = ctx.getCallableMethods()
        def results = callableMethods.producing(mockTypeMirror('Pet')).toList()
        results.size() == 1
        results[0].method.getSimpleName().toString() == 'adopt'
    }

    def 'producing matches covariantly with subtype return type'() {
        given:
        def typeElement = mockTypeElement('DogMapper')
        def petToAnimalMethod = mockMethod('adopt', 'Pet', 'Dog')
        def elements = Mock(Elements)
        elements.getAllMembers(typeElement) >> [petToAnimalMethod]
        def types = Mock(Types)
        types.isAssignable(_, _) >> { args ->
            def from = args[0]
            def to = args[1]
            def fromStr = from.toString()
            def toStr = to.toString()
            return fromStr == toStr || (fromStr == 'Pet' && toStr == 'Animal')
        }
        def stage = new DiscoverCallableMethods(elements, types)
        def ctx = new MapperContext(typeElement)

        when:
        stage.run(ctx)

        then:
        def callableMethods = ctx.getCallableMethods()
        def results = callableMethods.producing(mockTypeMirror('Animal')).toList()
        results.size() == 1
        results[0].method.getSimpleName().toString() == 'adopt'
    }

    def 'producing returns empty when no candidate matches'() {
        given:
        def typeElement = mockTypeElement('DogMapper')
        def method = mockMethod('adopt', 'Pet', 'Dog')
        def elements = Mock(Elements)
        elements.getAllMembers(typeElement) >> [method]
        def types = Mock(Types)
        types.isAssignable(_, _) >> { args -> args[0].toString() == args[1].toString() }
        def stage = new DiscoverCallableMethods(elements, types)
        def ctx = new MapperContext(typeElement)

        when:
        stage.run(ctx)

        then:
        def callableMethods = ctx.getCallableMethods()
        def results = callableMethods.producing(mockTypeMirror('UnrelatedType')).toList()
        results.isEmpty()
    }

    def 'MethodCandidate carries ThisReceiver.INSTANCE for all v1 candidates'() {
        given:
        def typeElement = mockTypeElement('DogMapper')
        def method = mockMethod('adopt', 'Pet', 'Dog')
        def elements = Mock(Elements)
        elements.getAllMembers(typeElement) >> [method]
        def types = Mock(Types)
        types.isAssignable(_, _) >> { args -> args[0].toString() == args[1].toString() }
        def stage = new DiscoverCallableMethods(elements, types)
        def ctx = new MapperContext(typeElement)

        when:
        stage.run(ctx)

        then:
        def callableMethods = ctx.getCallableMethods()
        def results = callableMethods.producing(mockTypeMirror('Pet')).toList()
        results.size() == 1
        results[0].receiver == ThisReceiver.INSTANCE
    }

    private TypeElement mockTypeElement(String name) {
        def typeElement = Mock(TypeElement)
        typeElement.qualifiedName >> mockName(name)
        typeElement
    }

    private ExecutableElement mockMethod(String returnName, String paramType) {
        def method = Mock(ExecutableElement)
        method.simpleName >> mockName(returnName)
        method.kind >> ElementKind.METHOD
        method.parameters >> []
        def returnType = mockTypeMirror(returnName)
        method.returnType >> returnType
        def enclosing = Mock(TypeElement)
        enclosing.qualifiedName >> mockName('SomeClass')
        method.enclosingElement >> enclosing
        method
    }

    private ExecutableElement mockMethod(String returnName, String returnType, String paramType) {
        def method = Mock(ExecutableElement)
        method.simpleName >> mockName(returnName)
        method.kind >> ElementKind.METHOD
        def param = Mock(VariableElement)
        param.asType() >> mockTypeMirror(paramType)
        method.parameters >> [param]
        def retType = mockTypeMirror(returnType)
        method.returnType >> retType
        def enclosing = Mock(TypeElement)
        enclosing.qualifiedName >> mockName('DogMapper')
        method.enclosingElement >> enclosing
        method
    }

    private TypeMirror mockTypeMirror(String typeName) {
        def typeElement = Mock(TypeElement)
        typeElement.qualifiedName >> mockName(typeName)
        def typeMirror = Mock(DeclaredType)
        typeMirror.asElement() >> typeElement
        typeMirror.kind >> TypeKind.DECLARED
        typeMirror.toString() >> typeName
        typeMirror
    }

    private Name mockName(String value) {
        def name = Mock(Name)
        name.toString() >> value
        name
    }
}
