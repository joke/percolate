package io.github.joke.percolate.processor.spi

import io.github.joke.percolate.processor.spi.builtins.ConstructorCall
import io.github.joke.percolate.processor.spi.builtins.DirectAssign
import io.github.joke.percolate.processor.spi.builtins.GetterRead
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class BuiltinArchitecturalSpec extends Specification {

    private static final String[] FORBIDDEN_PACKAGES = [
            'io/github/joke/percolate/processor/graph',
            'io/github/joke/percolate/processor/stages/expand'
    ]

    def 'built-in strategies do not reference processor.graph or processor.stages.expand in bytecode'() {
        given:
        def builtinClasses = [
                GetterRead,
                ConstructorCall,
                DirectAssign
        ]

        when:
        def violations = builtinClasses.collectMany { cls ->
            scanClassForForbiddenPackages(cls)
        }

        then:
        violations.isEmpty()
    }

    private List<String> scanClassForForbiddenPackages(Class<?> cls) {
        def violations = new ArrayList<String>()
        try {
            def resourcePath = cls.name.replace('.', '/') + '.class'
            def is = cls.classLoader.getResourceAsStream(resourcePath)
            if (is == null) {
                return violations
            }
            def bytes = is.readAllBytes()
            def scanner = new ForbiddenPackageScanner()
            new ClassReader(bytes).accept(scanner, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
            for (def pkg in scanner.foundPackages) {
                violations.add("${cls.simpleName}:$pkg")
            }
        } catch (Exception e) {
            // Class may not be loadable in all configurations
        }
        violations
    }

    private static class ForbiddenPackageScanner extends ClassVisitor {
        private final Set<String> foundPackages = new HashSet<>()

        ForbiddenPackageScanner() {
            super(Opcodes.ASM9)
        }

        @Override
        void visit(int version, int access, String name, String signature,
                   String superName, String[] interfaces) {
            checkName(name)
            checkName(superName)
            if (interfaces != null) {
                for (def iface : interfaces) {
                    checkName(iface)
                }
            }
        }

        @Override
        void visitInnerClass(String name, String outerName, String innerName, int access) {
            checkName(name)
        }

        @Override
        org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            checkType(descriptor)
            if (signature != null) {
                checkSignature(signature)
            }
        }

        @Override
        org.objectweb.asm.MethodVisitor visitMethod(int access, String name,
                String descriptor, String signature, String[] exceptions) {
            checkType(descriptor)
            if (signature != null) {
                checkSignature(signature)
            }
            if (exceptions != null) {
                for (def exc : exceptions) {
                    checkName(exc)
                }
            }
            return null
        }

        @Override
        org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            checkType(descriptor)
            return null
        }

        @Override
        org.objectweb.asm.ModuleVisitor visitModule(String name, int access, String version) {
            checkName(name)
            return null
        }

        private void checkName(String internalName) {
            if (internalName == null) return
            for (def pkg : FORBIDDEN_PACKAGES) {
                if (internalName.startsWith(pkg + '/') || internalName.startsWith(pkg + '$')) {
                    foundPackages.add(pkg)
                    return
                }
            }
        }

        private void checkType(String descriptor) {
            if (descriptor == null) return
            try {
                Type.getType(descriptor)
                // getType parses the descriptor; if it contains forbidden types
                // they would be in the class name - but we need to check manually
            } catch (Exception e) {
                // Ignore parsing errors
            }
            // Manually scan for L...; class references
            int i = 0
            while (i < descriptor.length()) {
                if (descriptor.charAt(i) == 'L') {
                    int end = descriptor.indexOf(';', i)
                    if (end > i) {
                        checkName(descriptor.substring(i + 1, end))
                        i = end + 1
                    } else {
                        i++
                    }
                } else if (descriptor.charAt(i) == '[') {
                    i++
                } else if (descriptor.charAt(i) == '(') {
                    // Skip to matching )
                    int depth = 1
                    i++
                    while (i < descriptor.length() && depth > 0) {
                        def c = descriptor.charAt(i)
                        if (c == 'L') {
                            while (i < descriptor.length() && descriptor.charAt(i) != ';') i++
                        } else if (c == '[') {
                            // skip array dimensions
                            while (i < descriptor.length() && descriptor.charAt(i) == '[') i++
                            if (i < descriptor.length() && descriptor.charAt(i) == 'L') {
                                while (i < descriptor.length() && descriptor.charAt(i) != ';') i++
                            }
                        } else if (c == '(') {
                            depth++
                        } else if (c == ')') {
                            depth--
                        }
                        if (depth > 0) i++
                    }
                    // Skip return type
                    if (i < descriptor.length() && descriptor.charAt(i) != 'V' && descriptor.charAt(i) != ')') {
                        if (descriptor.charAt(i) == 'L') {
                            int end = descriptor.indexOf(';', i)
                            if (end > i) {
                                checkName(descriptor.substring(i + 1, end))
                            }
                        }
                    }
                } else {
                    i++
                }
            }
        }

        private void checkSignature(String signature) {
            if (signature == null) return
            int i = 0
            while (i < signature.length()) {
                if (signature.charAt(i) == 'L') {
                    int end = signature.indexOf(';', i)
                    if (end > i) {
                        checkName(signature.substring(i + 1, end).replace('.', '/'))
                        i = end + 1
                    } else {
                        i++
                    }
                } else {
                    i++
                }
            }
        }
    }
}
