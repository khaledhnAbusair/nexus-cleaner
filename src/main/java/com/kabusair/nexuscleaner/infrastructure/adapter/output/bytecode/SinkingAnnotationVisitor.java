package com.kabusair.nexuscleaner.infrastructure.adapter.output.bytecode;

import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Set;

final class SinkingAnnotationVisitor extends AnnotationVisitor {

    private final Set<SymbolReference> sink;

    SinkingAnnotationVisitor(Set<SymbolReference> sink) {
        super(Opcodes.ASM9);
        this.sink = sink;
    }

    @Override
    public void visit(String name, Object value) {
        if (value instanceof Type t && t.getSort() == Type.OBJECT) {
            sink.add(SymbolReference.type(t.getClassName()));
        }
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
        addFromDescriptor(descriptor);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
        addFromDescriptor(descriptor);
        return this;
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
        return this;
    }

    private void addFromDescriptor(String descriptor) {
        if (descriptor == null) return;
        try {
            Type t = Type.getType(descriptor);
            if (t.getSort() == Type.OBJECT) {
                sink.add(SymbolReference.annotation(t.getClassName()));
            }
        } catch (IllegalArgumentException ignored) {
        }
    }
}
