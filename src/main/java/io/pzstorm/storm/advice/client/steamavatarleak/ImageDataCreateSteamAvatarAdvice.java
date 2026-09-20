package io.pzstorm.storm.advice.client.steamavatarleak;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

/**
 * Advice for {@code ImageData.createSteamAvatar(long)} that swaps the vanilla body for {@link
 * SteamAvatarImageData#create(long)}.
 *
 * <p>The enter advice always asks to skip the vanilla body, and the exit advice installs the
 * helper's result. {@code suppress} turns any advice failure into the default {@code false}, so the
 * vanilla body runs and its return value is left alone. The avatar travels as {@code Object} so the
 * inlined bytecode never names {@code ImageData}, the class being defined.
 */
public class ImageDataCreateSteamAvatarAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
    public static boolean onEnter(
            @Advice.Argument(0) long steamID, @Advice.Local("stormAvatar") Object avatar) {
        avatar = SteamAvatarImageData.create(steamID);
        return true;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
            @Advice.Enter boolean replaced,
            @Advice.Local("stormAvatar") Object avatar,
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object result) {
        if (replaced) {
            result = avatar;
        }
    }
}
