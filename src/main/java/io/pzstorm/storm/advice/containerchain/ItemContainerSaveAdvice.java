package io.pzstorm.storm.advice.containerchain;

import io.pzstorm.storm.patch.fixes.ContainerChainGuard;
import net.bytebuddy.asm.Advice;

public class ItemContainerSaveAdvice {
    @Advice.OnMethodEnter
    public static boolean onEnter(@Advice.This Object container) {
        return ContainerChainGuard.beginSave(container);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(@Advice.Enter boolean owner) {
        ContainerChainGuard.endSave(owner);
    }
}
