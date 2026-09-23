import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Offline: exercises the actual shipped compatibility gate without starting the server. */
class VerifyPackagedRespawn {
    public static void main(String[] args) throws Exception {
        Class<?> compatibility = Class.forName("onelife.worldessentials.safety.BuildCompatibility");
        Method supported = compatibility.getDeclaredMethod("supported");
        supported.setAccessible(true);
        if (!Boolean.TRUE.equals(supported.invoke(null))) {
            throw new IllegalStateException("Packaged respawn compatibility gate rejected the game JAR");
        }
        Class<?> bridge = Class.forName(
                "onelife.worldessentials.safety.RespawnConfigBridge", false,
                compatibility.getClassLoader());
        Field hash = bridge.getDeclaredField("LINUX_HASH");
        hash.setAccessible(true);
        String expected = "baa9213172885e82310a40886359fd831c766dc726469f60dd75831a51a2bbe0";
        if (!expected.equals(hash.get(null))) {
            throw new IllegalStateException("Packaged native allowlist changed");
        }
        System.out.println("PASS packaged respawn gate and unchanged native SHA-256 allowlist");
    }
}
