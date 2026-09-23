package io.pzstorm.storm.patch.client;

import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * Client-only. Frees the 64 KB buffer that {@code ImageData.createSteamAvatar(long)} strands every
 * time Steam has no avatar for the requested id.
 *
 * <p>Symptom: after some time on a busy server the world fills with a red-and-white checkerboard
 * (fog, weather, moodles, newly seen tiles), and the local character, zombies and vehicles stop
 * drawing. Nothing is logged. It lasts until the client restarts.
 *
 * <p>Chain: {@code ISScoreboard:fillList} calls {@code getSteamAvatarFromSteamID} for every online
 * player on every {@code OnScoreboardUpdate}. {@code Texture.getSteamAvatar} caches successes only,
 * so each avatar-less player costs one failed {@code createSteamAvatar} per refresh, and each
 * failure strands a buffer that {@code DirectBufferAllocator} keeps counting. Once the count passes
 * 50 MB, {@code TextureIDAssetManager.waitFileTask} parks all four file-system pool threads
 * forever. Every texture requested after that stays not-ready, and {@code Texture.bind} substitutes
 * {@code Texture.getErrorTexture()} for it. Staff hit it first because the admin mini scoreboard
 * re-requests the scoreboard on every connect and disconnect.
 *
 * <p>Why a client bytecode patch: the leak is a local variable inside a static Java method. Lua can
 * stop the scoreboard from asking, but {@code getSteamAvatarFromUsername}, {@code
 * SteamFriend.getAvatar} and any mod reach the same method, and no server change touches it.
 * Fail-soft: {@code suppress = Throwable.class} resolves an advice failure to "run vanilla", the
 * helper disposes its buffer on every non-success path, and a missing target method fails the
 * transform at weave time (logged, class left vanilla). Re-validate on each game update that {@code
 * createSteamAvatar} still has the shape copied into {@code SteamAvatarImageData}.
 */
public class ImageDataCreateSteamAvatarLeakPatch extends StormClassTransformer {

    private static final String TARGET = "zombie.core.textures.ImageData";
    private static final String ADVICE =
            "io.pzstorm.storm.advice.client.steamavatarleak.ImageDataCreateSteamAvatarAdvice";

    public ImageDataCreateSteamAvatarLeakPatch() {
        super(TARGET);
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        TypeDescription target = typePool.describe(TARGET).resolve();
        ElementMatcher.Junction<MethodDescription> createSteamAvatar =
                ElementMatchers.named("createSteamAvatar")
                        .and(ElementMatchers.isStatic())
                        .and(ElementMatchers.takesArguments(long.class));
        if (target.getDeclaredMethods().filter(createSteamAvatar).isEmpty()) {
            throw new IllegalStateException(
                    "ImageDataCreateSteamAvatarLeakPatch: ImageData no longer declares a static"
                            + " createSteamAvatar(long) — the hook would silently no-op and the"
                            + " avatar buffer leak would stall texture loading again. Re-verify"
                            + " the patch against the current game source.");
        }
        return builder.visit(
                Advice.to(typePool.describe(ADVICE).resolve(), locator).on(createSteamAvatar));
    }
}
