package com.kabusair.nexuscleaner.infrastructure.adapter.output.bytecode;

import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Set;

final class AnnotatedFieldVisitor extends FieldVisitor {

    private final Set<SymbolReference> sink;

    AnnotatedFieldVisitor(Set<SymbolReference> sink) {
        super(Opcodes.ASM9);
        this.sink = sink;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        addAnnotationType(descriptor);
        return new SinkingAnnotationVisitor(sink);
    }

    private void addAnnotationType(String descriptor) {
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
