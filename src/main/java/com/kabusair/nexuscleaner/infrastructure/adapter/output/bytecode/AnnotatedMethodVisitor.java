package com.kabusair.nexuscleaner.infrastructure.adapter.output.bytecode;

import com.kabusair.nexuscleaner.core.domain.model.SymbolReference;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;

import java.util.Set;

final class AnnotatedMethodVisitor extends SymbolCollectingMethodVisitor {

    private final Set<SymbolReference> annotationSink;

    AnnotatedMethodVisitor(Set<SymbolReference> sink) {
        super(sink, null);
        this.annotationSink = sink;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        addAnnotationType(descriptor);
        return new SinkingAnnotationVisitor(annotationSink);
    }

    private void addAnnotationType(String descriptor) {
        if (descriptor == null) return;
        try {
            Type t = Type.getType(descriptor);
            if (t.getSort() == Type.OBJECT) {
                annotationSink.add(SymbolReference.annotation(t.getClassName()));
            }
        } catch (IllegalArgumentException ignored) {
        }
    }
}
