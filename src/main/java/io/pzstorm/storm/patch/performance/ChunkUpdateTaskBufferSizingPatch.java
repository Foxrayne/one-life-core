package io.pzstorm.storm.patch.performance;

import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * Sizes {@code ChunkUpdateTask}'s direct buffer once from the chunk's level count instead of
 * letting {@code ensureCapacity} climb a 256-byte ladder one square at a time.
 *
 * <p>See {@link io.pzstorm.storm.advice.chunkupdatetaskbuffer.ChunkUpdateTaskInitAdvice} for the
 * measurement and the arithmetic.
 *
 * <p>The target is matched by name and arity, never by a hash of the class, so it survives both an
 * upstream Project Zomboid build and any transformer registered ahead of it.
 */
public class ChunkUpdateTaskBufferSizingPatch extends StormClassTransformer {

    private static final String PKG = "io.pzstorm.storm.advice.chunkupdatetaskbuffer.";

    public ChunkUpdateTaskBufferSizingPatch() {
        super("zombie.pathfind.nativeCode.ChunkUpdateTask");
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        return builder.visit(
                Advice.to(typePool.describe(PKG + "ChunkUpdateTaskInitAdvice").resolve(), locator)
                        .on(ElementMatchers.named("init").and(ElementMatchers.takesArguments(1))));
    }
}
