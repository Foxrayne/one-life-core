package io.pzstorm.storm.patch.fixes;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Pure logic behind {@link VehiclesDbChunkKeyPatch} and {@link VehiclesDbChunkKeyRepairPatch}:
 * keeps every {@code vehicles.db} row filed under the chunk that actually contains its {@code x,y}.
 *
 * <h2>The bug this heals</h2>
 *
 * <p>{@code VehiclesDB2$VehicleBuffer.set} writes {@code wx = vehicle.chunk.wx} but {@code x =
 * vehicle.getX()}. The chunk pointer only follows the vehicle while {@code BaseVehicle.update()}
 * can resolve a loaded square under it; a vehicle that moves with no loaded square keeps pointing
 * at its old {@code IsoChunk}. When that chunk unloads, {@code IsoChunk.Clear()} resets its {@code
 * wx/wy} to 0 and the object returns to the pool, so the next save files the vehicle under whatever
 * chunk the recycled object now describes. Chunk loads query {@code WHERE wx=? AND wy=?}, so the
 * row never loads at the vehicle's real position again: the car is gone for players (and its
 * inventory with it) while anything that reads the row's {@code x,y} still reports it parked. On a
 * busy server tens of vehicles a day pass through this state.
 *
 * <h2>The fix</h2>
 *
 * <p>Exit advice on {@code VehicleBuffer.set} overwrites {@code wx/wy} with {@code floor(x/8)},
 * {@code floor(y/8)} — the only key under which the load path ({@code chunk.getGridSquare(x - wx*8,
 * y - wy*8)}) can place the vehicle anyway. Enter advice on {@code SQLStore.initUsedIDs} rewrites
 * already-orphaned rows the same way before vanilla builds its seen-chunks set from the table, so a
 * restart recovers vehicles that earlier sessions misfiled.
 */
public final class VehiclesDbChunkKeyFix {

    private static final int CHUNK_SQUARES = 8;

    private VehiclesDbChunkKeyFix() {}

    public static int chunkCoord(float worldCoord) {
        return (int) Math.floor(worldCoord / CHUNK_SQUARES);
    }

    public static void logRefiled(
            int id, int wx, int wy, int realWx, int realWy, float x, float y) {
        LOGGER.warn(
                "VehiclesDbChunkKeyPatch: vehicle sqlId={} at ({}, {}) pointed at chunk ({}, {}),"
                        + " filed under ({}, {})",
                id,
                x,
                y,
                wx,
                wy,
                realWx,
                realWy);
    }

    public static void repair(Connection conn) {
        int scanned = 0;
        int fixed = 0;
        try (PreparedStatement select =
                        conn.prepareStatement("SELECT id, wx, wy, x, y FROM vehicles");
                PreparedStatement update =
                        conn.prepareStatement("UPDATE vehicles SET wx = ?, wy = ? WHERE id = ?")) {
            ResultSet rs = select.executeQuery();
            while (rs.next()) {
                scanned++;
                int id = rs.getInt(1);
                int wx = rs.getInt(2);
                int wy = rs.getInt(3);
                float x = rs.getFloat(4);
                if (rs.wasNull()) {
                    continue;
                }
                float y = rs.getFloat(5);
                if (rs.wasNull()) {
                    continue;
                }
                int realWx = chunkCoord(x);
                int realWy = chunkCoord(y);
                if (wx == realWx && wy == realWy) {
                    continue;
                }
                update.setInt(1, realWx);
                update.setInt(2, realWy);
                update.setInt(3, id);
                update.executeUpdate();
                fixed++;
                LOGGER.warn(
                        "VehiclesDbChunkKeyRepairPatch: vehicle sqlId={} at ({}, {}) was filed under"
                                + " chunk ({}, {}), moved to ({}, {})",
                        id,
                        x,
                        y,
                        wx,
                        wy,
                        realWx,
                        realWy);
            }
            if (fixed > 0) {
                conn.commit();
            }
            LOGGER.info(
                    "VehiclesDbChunkKeyRepairPatch: scanned {} vehicles.db rows, re-filed {}",
                    scanned,
                    fixed);
        } catch (SQLException e) {
            LOGGER.error("VehiclesDbChunkKeyRepairPatch: repair failed after {} fixes", fixed, e);
            try {
                conn.rollback();
            } catch (SQLException rollbackFailure) {
                LOGGER.error("VehiclesDbChunkKeyRepairPatch: rollback failed", rollbackFailure);
            }
        }
    }
}
