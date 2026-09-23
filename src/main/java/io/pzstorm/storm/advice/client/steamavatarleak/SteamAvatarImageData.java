package io.pzstorm.storm.advice.client.steamavatarleak;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import java.util.concurrent.atomic.AtomicLong;
import zombie.core.textures.ImageData;
import zombie.core.utils.DirectBufferAllocator;
import zombie.core.utils.WrappedBuffer;
import zombie.core.znet.SteamFriends;

/**
 * Replacement body for {@code ImageData.createSteamAvatar(long)}, invoked from {@link
 * ImageDataCreateSteamAvatarAdvice}.
 *
 * <p>Vanilla allocates a 64 KB {@link WrappedBuffer}, asks Steam to fill it, and returns {@code
 * null} without disposing the buffer when Steam has no avatar for that id yet. {@code
 * Texture.getSteamAvatar} caches successes only, so every scoreboard refresh retries every
 * avatar-less player and strands another 64 KB each. {@code DirectBufferAllocator} counts stranded
 * buffers as live, and {@code TextureIDAssetManager.waitFileTask} parks every texture-loader thread
 * while that count is above 50 MB. About 800 failed lookups therefore stop all texture loading for
 * the rest of the session: unloaded textures bind the red-and-white error checkerboard, and
 * characters, zombies and vehicles whose textures never arrive are not drawn.
 *
 * <p>This body is the vanilla one plus the missing dispose.
 */
public class SteamAvatarImageData {

    public static final int AVATAR_BUFFER_BYTES = 65536;

    /** Lookups Steam could not answer; each one leaked a buffer in vanilla. */
    public static final AtomicLong UNAVAILABLE = new AtomicLong();

    /** Lookups that threw; the buffer was disposed and no avatar was returned. */
    public static final AtomicLong FAILED = new AtomicLong();

    /** Returns the avatar as an {@code ImageData}, or {@code null} when Steam has none. */
    public static Object create(long steamID) {
        WrappedBuffer data = DirectBufferAllocator.allocate(AVATAR_BUFFER_BYTES);
        boolean handedOff = false;
        try {
            int avatarWidth = SteamFriends.CreateSteamAvatar(steamID, data.getBuffer());
            if (avatarWidth <= 0) {
                UNAVAILABLE.incrementAndGet();
                return null;
            }
            int avatarHeight = data.getBuffer().position() / (avatarWidth * 4);
            data.getBuffer().flip();
            ImageData image = new ImageData(avatarWidth, avatarHeight, data);
            handedOff = true;
            return image;
        } catch (Throwable t) {
            if (FAILED.incrementAndGet() == 1) {
                LOGGER.error(
                        "ImageDataCreateSteamAvatarLeakPatch: Steam avatar lookup failed; returning"
                                + " no avatar",
                        t);
            }
            return null;
        } finally {
            if (!handedOff && !data.isDisposed()) {
                data.dispose();
            }
        }
    }
}
