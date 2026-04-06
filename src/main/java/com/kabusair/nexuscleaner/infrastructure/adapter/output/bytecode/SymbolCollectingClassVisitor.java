package com.kabusair.nexuscleaner.infrastructure.adapter.output.bytecode;

import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;
import org.objectweb.asm.*;

import java.util.Set;

import static com.kabusair.nexuscleaner.infrastructure.adapter.output.bytecode.SymbolCollectingMethodVisitor.internalToFqcn;

final class SymbolCollectingClassVisitor extends ClassVisitor {

    private final Set<SymbolReference> sink;

    SymbolCollectingClassVisitor(Set<SymbolReference> sink) {
        super(Opcodes.ASM9);
        this.sink = sink;
    }

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        if (superName != null) {
            sink.add(SymbolReference.type(internalToFqcn(superName)));
        }
        if (interfaces != null) {
            for (String iface : interfaces) {
                sink.add(SymbolReference.type(internalToFqcn(iface)));
            }
        }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        addTypeFromDescriptor(descriptor, true);
        return new SinkingAnnotationVisitor(sink);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath,
                                                 String descriptor, boolean visible) {
        addTypeFromDescriptor(descriptor, true);
        return new SinkingAnnotationVisitor(sink);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor,
                                   String signature, Object value) {
        addTypeFromDescriptor(descriptor, false);
        return new AnnotatedFieldVisitor(sink);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                     String signature, String[] exceptions) {
        collectMethodSignatureTypes(descriptor, exceptions);
        return new AnnotatedMethodVisitor(sink);
    }

    private void collectMethodSignatureTypes(String descriptor, String[] exceptions) {
        Type methodType = Type.getMethodType(descriptor);
        for (Type arg : methodType.getArgumentTypes()) {
            addTypeIfObject(arg);
        }
        addTypeIfObject(methodType.getReturnType());
        if (exceptions != null) {
            for (String ex : exceptions) {
                sink.add(SymbolReference.type(internalToFqcn(ex)));
            }
        }
    }

    private void addTypeFromDescriptor(String descriptor, boolean asAnnotation) {
        if (descriptor == null) return;
        try {
            Type t = Type.getType(descriptor);
            if (t.getSort() == Type.OBJECT) {
                sink.add(asAnnotation
                        ? SymbolReference.annotation(t.getClassName())
                        : SymbolReference.type(t.getClassName()));
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void addTypeIfObject(Type t) {
        if (t == null) return;
        if (t.getSort() == Type.OBJECT) {
            sink.add(SymbolReference.type(t.getClassName()));
        } else if (t.getSort() == Type.ARRAY) {
            addTypeIfObject(t.getElementType());
        }
    }
}
