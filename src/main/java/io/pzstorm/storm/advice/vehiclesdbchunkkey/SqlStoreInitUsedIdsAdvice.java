package io.pzstorm.storm.advice.vehiclesdbchunkkey;

import io.pzstorm.storm.patch.fixes.VehiclesDbChunkKeyFix;
import java.sql.Connection;
import net.bytebuddy.asm.Advice;

/**
 * Inlined at the start of {@code VehiclesDB2$SQLStore.initUsedIDs}. Re-files every {@code
 * vehicles.db} row whose {@code wx/wy} disagree with its {@code x,y} before the body reads the
 * table to build the seen-chunks set. See {@link
 * io.pzstorm.storm.patch.fixes.VehiclesDbChunkKeyRepairPatch}.
 */
public class SqlStoreInitUsedIdsAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.FieldValue("conn") Connection conn) {
        VehiclesDbChunkKeyFix.repair(conn);
    }
}
