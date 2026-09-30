package io.pzstorm.storm.patch.fixes;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.bytebuddy.dynamic.ClassFileLocator;

/** Exact reviewed upstream fixes; an unknown update must not silently retire a patch. */
final class ReviewedVanillaFix {
    private ReviewedVanillaFix() {}

    static boolean matches(ClassFileLocator locator, String className, String sha256) {
        try {
            byte[] bytes = locator.locate(className).resolve();
            return sha256.equals(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot verify upstream fix for " + className, e);
        }
    }
}
