package io.pzstorm.launcher;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Minimal Steamworks UGC binding via java.lang.foreign — no SDK jar, no JNI glue. Lets the launcher
 * ask the RUNNING Steam client to download/update workshop items into Steam's own content directory
 * before the game starts. We never write into steamapps ourselves; Steam does, through its
 * supported API.
 *
 * <p>Must run with working directory = the game install (steam_appid.txt) and with the same OS's
 * Steam client running. The launcher therefore invokes this in a child JVM ({@code --steam-update})
 * spawned with cwd = gameDir.
 */
public final class SteamUgc implements AutoCloseable {

    // EItemState bits (Steamworks ISteamUGC)
    static final int STATE_INSTALLED = 4;
    static final int STATE_SUBSCRIBED = 1;
    static final int STATE_NEEDS_UPDATE = 8;
    static final int STATE_DOWNLOADING = 16;
    static final int STATE_DOWNLOAD_PENDING = 32;

    /** DownloadItemResult_t: k_iSteamUGCCallbacks (3400) + 6. */
    static final int DOWNLOAD_ITEM_RESULT_CALLBACK = 3406;

    /** k_EResultOK. */
    static final int RESULT_OK = 1;

    private static final long POLL_MILLIS = 100;
    private static final long STALL_TIMEOUT_MILLIS = 180_000;
    private static final long SUBSCRIBE_TIMEOUT_MILLIS = 30_000;

    /**
     * Settle window after DownloadItem is accepted: Steam gets this long to either deliver a
     * DownloadItemResult_t or start reporting download activity before we conclude the item was
     * already current. The final GetItemState check still gates the verdict.
     */
    private static final long SETTLE_GRACE_MILLIS = 5_000;

    /** Prints every drained callback (id, size) — for field-debugging the dispatch plumbing. */
    private static final boolean DEBUG = Boolean.getBoolean("storm.steamugc.debug");

    private final Arena arena;
    private final MethodHandle shutdown;
    private final MethodHandle runCallbacks;
    private final MethodHandle subscribeItem;
    private final MethodHandle unsubscribeItem;
    private final MethodHandle downloadItem;
    private final MethodHandle getItemState;
    private final MethodHandle getItemDownloadInfo;
    private final MethodHandle dispatchRunFrame;
    private final MethodHandle dispatchGetNext;
    private final MethodHandle dispatchFreeLast;
    private final MemorySegment ugc;
    private final MemorySegment callbackMsg;

    /** Steam pipe handle for manual callback dispatch; 0 = unavailable, use the grace fallback. */
    private final int pipe;

    /** Steamworks callback structs are pack(4) on linux/macOS, pack(8) on Windows. */
    private final boolean packSmall;

    /** DownloadItemResult_t received so far: workshop item id -> EResult. */
    private final Map<Long, Integer> downloadResults = new HashMap<>();

    /**
     * Optional exports behind {@link #publishedTimes} and {@link #installTimestamp}. An older
     * steam_api may lack any of them; those methods then report "unavailable" and callers fall back
     * to the full {@link #updateItem} path. A missing export never fails {@link #connect}.
     */
    private final MethodHandle createQueryUGCDetailsRequest;

    private final MethodHandle sendQueryUGCRequest;
    private final MethodHandle getQueryUGCResult;
    private final MethodHandle releaseQueryUGCRequest;
    private final MethodHandle getItemInstallInfo;
    private final MethodHandle dispatchGetAPICallResult;

    /**
     * SteamAPICallCompleted_t: k_iSteamUtilsCallbacks (700) + 3. {@code SteamAPICall_t
     * m_hAsyncCall} at 0, {@code int m_iCallback} at 8, {@code uint32 m_cubParam} at 12 on both
     * pack sizes.
     */
    private static final int API_CALL_COMPLETED_CALLBACK = 703;

    /** SteamUGCQueryCompleted_t: k_iSteamUGCCallbacks (3400) + 1. */
    private static final int UGC_QUERY_COMPLETED_CALLBACK = 3401;

    private static final int PUBLISHED_TIMES_BATCH_SIZE = 50;
    private static final long PUBLISHED_TIMES_TIMEOUT_MILLIS = 15_000;

    /** SteamUGCDetails_t is ~9.8 KB on either pack size; headroom so Steam can't overrun it. */
    private static final long DETAILS_STRUCT_SIZE = 16_384;

    private static final int INSTALL_FOLDER_BUFFER_SIZE = 1024;

    /** 2010-01-01T00:00:00Z, the floor for a plausible m_rtimeUpdated. */
    private static final long EARLIEST_SANE_TIME_UPDATED = 1_262_304_000L;

    private static final long ONE_DAY_SECONDS = 86_400;

    /** A call result fetched by {@link #pumpCallbacks}; {@code data} is valid only when !failed. */
    private record ApiCallResult(boolean failed, int callbackId, MemorySegment data) {}

    /** Call results ready for pickup, keyed by SteamAPICall_t handle. */
    private final Map<Long, ApiCallResult> apiCallResults = new HashMap<>();

    /** SteamAPICall_t handles {@link #publishedTimes} is waiting on. */
    private final Set<Long> pendingApiCalls = new HashSet<>();

    private SteamUgc(Arena arena, SymbolLookup lib) throws Throwable {
        this.arena = arena;
        Linker linker = Linker.nativeLinker();

        MethodHandle initFlat =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_InitFlat",
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
                        true);
        shutdown = downcall(linker, lib, "SteamAPI_Shutdown", FunctionDescriptor.ofVoid(), false);
        runCallbacks =
                downcall(linker, lib, "SteamAPI_RunCallbacks", FunctionDescriptor.ofVoid(), false);
        MethodHandle ugcAccessor =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_SteamUGC_v021",
                        FunctionDescriptor.of(ValueLayout.ADDRESS),
                        false);
        subscribeItem =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_ISteamUGC_SubscribeItem",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
                        false);
        unsubscribeItem =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_ISteamUGC_UnsubscribeItem",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
                        false);
        downloadItem =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_ISteamUGC_DownloadItem",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_BOOLEAN,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_LONG,
                                ValueLayout.JAVA_BOOLEAN),
                        false);
        getItemState =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_ISteamUGC_GetItemState",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
                        false);
        getItemDownloadInfo =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_ISteamUGC_GetItemDownloadInfo",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_BOOLEAN,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_LONG,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS),
                        false);
        MethodHandle dispatchInit =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_ManualDispatch_Init",
                        FunctionDescriptor.ofVoid(),
                        true);
        MethodHandle getSteamPipe =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_GetHSteamPipe",
                        FunctionDescriptor.of(ValueLayout.JAVA_INT),
                        true);
        dispatchRunFrame =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_ManualDispatch_RunFrame",
                        FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT),
                        true);
        dispatchGetNext =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_ManualDispatch_GetNextCallback",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_BOOLEAN,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS),
                        true);
        dispatchFreeLast =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_ManualDispatch_FreeLastCallback",
                        FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT),
                        true);
        boolean manualDispatch =
                dispatchInit != null
                        && getSteamPipe != null
                        && dispatchRunFrame != null
                        && dispatchGetNext != null
                        && dispatchFreeLast != null;
        if (manualDispatch) {
            // current SDKs require this BEFORE SteamAPI_Init to reroute callbacks to the manual
            // queue; the PZ mac depot's older steam_api rejects the pre-init call ("must init
            // library first"), so it is repeated after init below
            dispatchInit.invoke();
        }

        if (initFlat != null) {
            MemorySegment errMsg = arena.allocate(1024);
            int result = (int) initFlat.invoke(errMsg);
            if (result != 0) {
                throw new SteamException(
                        "SteamAPI_Init failed ("
                                + result
                                + "): "
                                + errMsg.getString(0)
                                + " — is Steam running?");
            }
        } else {
            MethodHandle initSafe =
                    downcall(
                            linker,
                            lib,
                            "SteamAPI_InitSafe",
                            FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN),
                            false);
            if (!(boolean) initSafe.invoke()) {
                throw new SteamException("SteamAPI_Init failed — is Steam running?");
            }
        }
        if (manualDispatch) {
            // second call for the older mac steam_api, which only accepts it after init; on
            // current SDKs the dispatch flag is already set and this is a no-op
            dispatchInit.invoke();
        }
        this.ugc = (MemorySegment) ugcAccessor.invoke();
        if (ugc.equals(MemorySegment.NULL)) {
            throw new SteamException("SteamUGC interface unavailable");
        }
        this.pipe = manualDispatch ? (int) getSteamPipe.invoke() : 0;
        // CallbackMsg_t: int32 user, int32 callbackId, param*, int32 paramLen — 24 bytes on x64
        this.callbackMsg = arena.allocate(24);
        String os = System.getProperty("os.name", "").toLowerCase();
        this.packSmall = !os.contains("win");

        // Signatures and struct layouts checked against Steamworks.NET
        // (rlabrecque/Steamworks.NET@ba71581f, autogen/NativeMethods.cs, SteamStructs.cs) and
        // gbe_fork (Detanup01/gbe_fork, sdk/steam/isteamugc.h), matching SteamAPI_SteamUGC_v021.
        createQueryUGCDetailsRequest =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_ISteamUGC_CreateQueryUGCDetailsRequest",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_LONG,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT),
                        true);
        sendQueryUGCRequest =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_ISteamUGC_SendQueryUGCRequest",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
                        true);
        getQueryUGCResult =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_ISteamUGC_GetQueryUGCResult",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_BOOLEAN,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_LONG,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS),
                        true);
        releaseQueryUGCRequest =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_ISteamUGC_ReleaseQueryUGCRequest",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_BOOLEAN,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_LONG),
                        true);
        getItemInstallInfo =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_ISteamUGC_GetItemInstallInfo",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_BOOLEAN,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_LONG,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS),
                        true);
        dispatchGetAPICallResult =
                downcall(
                        linker,
                        lib,
                        "SteamAPI_ManualDispatch_GetAPICallResult",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_BOOLEAN,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_LONG,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS),
                        true);
    }

    private static MethodHandle downcall(
            Linker linker,
            SymbolLookup lib,
            String name,
            FunctionDescriptor descriptor,
            boolean optional) {
        Optional<MemorySegment> symbol = lib.find(name);
        if (symbol.isEmpty()) {
            if (optional) {
                return null;
            }
            throw new SteamException("steam_api is missing export " + name);
        }
        return linker.downcallHandle(symbol.get(), descriptor);
    }

    public static class SteamException extends RuntimeException {
        public SteamException(String message) {
            super(message);
        }
    }

    /** Locates the steam_api library inside the game install. */
    static Path findLibrary(Path gameDir) {
        String os = System.getProperty("os.name", "").toLowerCase();
        List<Path> candidates;
        if (os.contains("win")) {
            candidates = List.of(gameDir.resolve("steam_api64.dll"));
        } else if (os.contains("mac")) {
            candidates = List.of(gameDir.resolve("libsteam_api.dylib"));
        } else {
            candidates =
                    List.of(
                            gameDir.resolve("linux64").resolve("libsteam_api.so"),
                            gameDir.resolve("libsteam_api.so"));
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new SteamException("steam_api library not found under " + gameDir);
    }

    public static SteamUgc connect(Path gameDir) {
        Arena arena = Arena.ofShared();
        try {
            return new SteamUgc(arena, SymbolLookup.libraryLookup(findLibrary(gameDir), arena));
        } catch (SteamException e) {
            arena.close();
            throw e;
        } catch (Throwable t) {
            arena.close();
            throw new SteamException("Steam binding failed: " + t);
        }
    }

    /**
     * Bring one workshop item to the exact state the game's join gate demands: {@code
     * Subscribed|Installed} with no update pending (ConnectToServerState requires equality with 5 —
     * a merely-downloaded, unsubscribed item still triggers the in-game prompt screen). Subscribing
     * is what the vanilla join flow does for unsubscribed items, and it makes Steam keep the item
     * current.
     *
     * <p>Always calls DownloadItem, even when GetItemState claims the item is current: that state
     * only reflects the Steam client's CACHED metadata, which is stale from the moment a new
     * version is published until Steam happens to re-poll the workshop. DownloadItem (high
     * priority) forces the check against the latest published manifest right now; its
     * DownloadItemResult_t callback is the authoritative completion signal.
     */
    public boolean updateItem(long itemId, Consumer<String> progress) throws InterruptedException {
        int state = itemState(itemId);
        if ((state & STATE_SUBSCRIBED) == 0) {
            try {
                subscribeItem.invoke(ugc, itemId);
            } catch (Throwable t) {
                throw new SteamException("SubscribeItem(" + itemId + ") failed: " + t);
            }
            progress.accept("item " + itemId + " subscribing …");
            if (!awaitSubscribedIs(itemId, true, "subscribe", progress)) {
                return false;
            }
        }
        downloadResults.remove(itemId);
        boolean accepted;
        try {
            accepted = (boolean) downloadItem.invoke(ugc, itemId, true);
        } catch (Throwable t) {
            throw new SteamException("DownloadItem(" + itemId + ") failed: " + t);
        }
        if (!accepted) {
            progress.accept("item " + itemId + " rejected by Steam (invalid id?)");
            return false;
        }
        progress.accept("item " + itemId + " checking for updates …");

        boolean sawDownload = false;
        long lastProgressBytes = -1;
        long lastActivity = System.currentTimeMillis();
        while (true) {
            pumpCallbacks();
            state = itemState(itemId);
            if ((state & (STATE_DOWNLOADING | STATE_DOWNLOAD_PENDING)) != 0) {
                sawDownload = true;
                long[] downloaded = downloadInfo(itemId);
                if (downloaded != null && downloaded[0] != lastProgressBytes) {
                    lastProgressBytes = downloaded[0];
                    lastActivity = System.currentTimeMillis();
                    if (downloaded[1] > 0) {
                        progress.accept(
                                "item "
                                        + itemId
                                        + " downloading "
                                        + downloaded[0]
                                        + "/"
                                        + downloaded[1]
                                        + " bytes");
                    }
                } else if (System.currentTimeMillis() - lastActivity > STALL_TIMEOUT_MILLIS) {
                    progress.accept(
                            "item "
                                    + itemId
                                    + " STALLED (no progress for "
                                    + (STALL_TIMEOUT_MILLIS / 1000)
                                    + "s) — check Steam downloads");
                    return false;
                }
            } else {
                if (sawDownload) {
                    // busy -> idle transition: Steam finished (or aborted) the install; the
                    // final state check decides which
                    return reportFinalState(itemId, sawDownload, progress);
                }
                Integer result = downloadResults.get(itemId);
                if (result != null) {
                    if (result != RESULT_OK) {
                        progress.accept(
                                "item "
                                        + itemId
                                        + " FAILED (Steam download result "
                                        + result
                                        + ")");
                        return false;
                    }
                    return reportFinalState(itemId, sawDownload, progress);
                }
                if (System.currentTimeMillis() - lastActivity > SETTLE_GRACE_MILLIS) {
                    // no result callback and no download ever started: the item was current
                    return reportFinalState(itemId, sawDownload, progress);
                }
            }
            Thread.sleep(POLL_MILLIS);
        }
    }

    /**
     * Fast path for an item the caller already proved current against the published workshop
     * metadata (acf install timestamp equals published {@code time_updated} — the game's own join
     * gate comparison). Only the LOCAL user state still needs confirming: the subscribed/installed
     * bits are user state, not the cached published metadata that makes {@link #updateItem}
     * distrust GetItemState, so join-ready here is instant and authoritative. Anything else
     * escalates to the full update path — never a worse outcome, just slower.
     */
    public boolean verifyItem(long itemId, Consumer<String> progress) throws InterruptedException {
        int state = itemState(itemId);
        if (isJoinReady(state)) {
            progress.accept("item " + itemId + " up to date");
            return true;
        }
        progress.accept("item " + itemId + " not join-ready (state=" + state + ") — updating …");
        return updateItem(itemId, progress);
    }

    /**
     * Recovery for an install record Steam refuses to refresh: when the item's local content
     * already matches the published manifest, {@link #updateItem}'s forced DownloadItem verifies
     * and completes without moving a byte — and without rewriting the install metadata the game's
     * join gate compares ({@code GetItemInstallTimeStamp} vs published {@code time_updated}), so
     * the game re-requests the download forever. Cycling the subscription discards the desynced
     * item record; the caller has already deleted the item's content directory, so the follow-up
     * {@link #updateItem} performs a real download and Steam commits fresh install metadata,
     * timestamp included.
     */
    public boolean repairItem(long itemId, Consumer<String> progress) throws InterruptedException {
        if ((itemState(itemId) & STATE_SUBSCRIBED) != 0) {
            try {
                unsubscribeItem.invoke(ugc, itemId);
            } catch (Throwable t) {
                throw new SteamException("UnsubscribeItem(" + itemId + ") failed: " + t);
            }
            progress.accept("item " + itemId + " unsubscribing to reset its install record …");
            if (!awaitSubscribedIs(itemId, false, "unsubscribe", progress)) {
                return false;
            }
        }
        return updateItem(itemId, progress);
    }

    private boolean awaitSubscribedIs(
            long itemId, boolean subscribed, String action, Consumer<String> progress)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + SUBSCRIBE_TIMEOUT_MILLIS;
        while (((itemState(itemId) & STATE_SUBSCRIBED) != 0) != subscribed) {
            if (System.currentTimeMillis() > deadline) {
                progress.accept(
                        "item "
                                + itemId
                                + " FAILED ("
                                + action
                                + " timed out — invalid"
                                + " id or Steam offline?)");
                return false;
            }
            pumpCallbacks();
            Thread.sleep(POLL_MILLIS);
        }
        return true;
    }

    private boolean reportFinalState(long itemId, boolean sawDownload, Consumer<String> progress) {
        int state = itemState(itemId);
        boolean ok = isJoinReady(state);
        if (ok) {
            progress.accept("item " + itemId + (sawDownload ? " updated" : " up to date"));
        } else if (!sawDownload && (state & STATE_NEEDS_UPDATE) != 0) {
            // Steam answered the DownloadItem call but never moved a byte and left the item
            // needing an update — a stuck Steam client, cleared by restarting Steam
            progress.accept(
                    "item "
                            + itemId
                            + " FAILED (state="
                            + state
                            + " — Steam refused the download; restart Steam, let it finish any"
                            + " workshop verification it begins, then press Join again)");
        } else {
            progress.accept("item " + itemId + " FAILED (state=" + state + ")");
        }
        return ok;
    }

    /** The game's join gate requires exactly Subscribed|Installed and nothing pending. */
    static boolean isJoinReady(int state) {
        return (state & STATE_SUBSCRIBED) != 0
                && (state & STATE_INSTALLED) != 0
                && (state & (STATE_NEEDS_UPDATE | STATE_DOWNLOADING | STATE_DOWNLOAD_PENDING)) == 0;
    }

    private int itemState(long itemId) {
        try {
            return (int) getItemState.invoke(ugc, itemId);
        } catch (Throwable t) {
            throw new SteamException("GetItemState(" + itemId + ") failed: " + t);
        }
    }

    /** [bytesDownloaded, bytesTotal] or null when Steam has no download info yet. */
    private long[] downloadInfo(long itemId) {
        try {
            MemorySegment done = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment total = arena.allocate(ValueLayout.JAVA_LONG);
            boolean known = (boolean) getItemDownloadInfo.invoke(ugc, itemId, done, total);
            if (!known) {
                return null;
            }
            return new long[] {
                done.get(ValueLayout.JAVA_LONG, 0), total.get(ValueLayout.JAVA_LONG, 0)
            };
        } catch (Throwable t) {
            throw new SteamException("GetItemDownloadInfo(" + itemId + ") failed: " + t);
        }
    }

    /**
     * With manual dispatch (any steam_api from the last several years) this drains the callback
     * queue and records DownloadItemResult_t payloads and the call results {@link #publishedTimes}
     * waits on; without it, it falls back to the classic RunCallbacks pump and {@link #updateItem}
     * settles on a grace timer instead.
     */
    private void pumpCallbacks() {
        try {
            if (pipe == 0) {
                runCallbacks.invoke();
                return;
            }
            dispatchRunFrame.invoke(pipe);
            while ((boolean) dispatchGetNext.invoke(pipe, callbackMsg)) {
                try {
                    int callbackId = callbackMsg.get(ValueLayout.JAVA_INT, 4);
                    int paramLen = callbackMsg.get(ValueLayout.JAVA_INT, 16);
                    if (DEBUG) {
                        System.out.println(
                                "[debug] callback id=" + callbackId + " len=" + paramLen);
                    }
                    if (callbackId == DOWNLOAD_ITEM_RESULT_CALLBACK
                            && paramLen >= (packSmall ? 16 : 24)) {
                        MemorySegment data =
                                callbackMsg.get(ValueLayout.ADDRESS, 8).reinterpret(paramLen);
                        downloadResults.put(
                                downloadResultItemId(data, packSmall),
                                downloadResultCode(data, packSmall));
                    } else if (callbackId == API_CALL_COMPLETED_CALLBACK && paramLen >= 16) {
                        MemorySegment data =
                                callbackMsg.get(ValueLayout.ADDRESS, 8).reinterpret(paramLen);
                        long asyncCall = data.get(ValueLayout.JAVA_LONG_UNALIGNED, 0);
                        if (pendingApiCalls.contains(asyncCall)) {
                            int callbackType = data.get(ValueLayout.JAVA_INT_UNALIGNED, 8);
                            int cubParam = data.get(ValueLayout.JAVA_INT_UNALIGNED, 12);
                            MemorySegment resultData = arena.allocate(Math.max(cubParam, 1));
                            MemorySegment failedFlag = arena.allocate(ValueLayout.JAVA_BOOLEAN);
                            boolean available =
                                    dispatchGetAPICallResult != null
                                            && (boolean)
                                                    dispatchGetAPICallResult.invoke(
                                                            pipe,
                                                            asyncCall,
                                                            resultData,
                                                            cubParam,
                                                            callbackType,
                                                            failedFlag);
                            if (available) {
                                boolean failed = failedFlag.get(ValueLayout.JAVA_BOOLEAN, 0);
                                apiCallResults.put(
                                        asyncCall,
                                        new ApiCallResult(failed, callbackType, resultData));
                                pendingApiCalls.remove(asyncCall);
                            }
                        }
                    }
                } finally {
                    dispatchFreeLast.invoke(pipe);
                }
            }
        } catch (Throwable t) {
            throw new SteamException("Steam callback pump failed: " + t);
        }
    }

    /**
     * DownloadItemResult_t is AppId_t (int32), PublishedFileId_t (int64), EResult (int32). Valve
     * packs callback structs at 4 bytes on linux/macOS and 8 on Windows, which moves the 64-bit
     * file id to offset 4 or 8 respectively.
     */
    static long downloadResultItemId(MemorySegment data, boolean packSmall) {
        return data.get(ValueLayout.JAVA_LONG_UNALIGNED, packSmall ? 4 : 8);
    }

    static int downloadResultCode(MemorySegment data, boolean packSmall) {
        return data.get(ValueLayout.JAVA_INT_UNALIGNED, packSmall ? 12 : 16);
    }

    /**
     * Published {@code time_updated} per item as the logged-in user sees it, which covers private
     * and friends-only items the anonymous Web API answers with {@code result} 9. One
     * CreateQueryUGCDetailsRequest round trip per {@value #PUBLISHED_TIMES_BATCH_SIZE} ids.
     *
     * <p>Returns empty, never a partial answer, when the query can't be trusted: a missing export,
     * no manual dispatch, a failed or timed-out call, a non-OK result, or a struct that fails the
     * layout sanity check. Callers then fall back to the full update for every id.
     */
    Optional<Map<Long, Long>> publishedTimes(List<Long> itemIds) throws InterruptedException {
        if (itemIds.isEmpty()) {
            return Optional.of(Map.of());
        }
        if (pipe == 0
                || createQueryUGCDetailsRequest == null
                || sendQueryUGCRequest == null
                || getQueryUGCResult == null
                || releaseQueryUGCRequest == null
                || dispatchGetAPICallResult == null) {
            return Optional.empty();
        }
        Set<Long> requested = new HashSet<>(itemIds);
        Map<Long, Long> published = new HashMap<>();
        for (int start = 0; start < itemIds.size(); start += PUBLISHED_TIMES_BATCH_SIZE) {
            List<Long> batch =
                    itemIds.subList(
                            start, Math.min(start + PUBLISHED_TIMES_BATCH_SIZE, itemIds.size()));
            Optional<Map<Long, Long>> batchResult = queryBatch(batch, requested);
            if (batchResult.isEmpty()) {
                return Optional.empty();
            }
            published.putAll(batchResult.get());
        }
        return Optional.of(published);
    }

    private Optional<Map<Long, Long>> queryBatch(List<Long> batch, Set<Long> requested)
            throws InterruptedException {
        MemorySegment ids = arena.allocate(ValueLayout.JAVA_LONG, batch.size());
        for (int i = 0; i < batch.size(); i++) {
            ids.setAtIndex(ValueLayout.JAVA_LONG, i, batch.get(i));
        }
        long handle;
        try {
            handle = (long) createQueryUGCDetailsRequest.invoke(ugc, ids, batch.size());
        } catch (Throwable t) {
            return Optional.empty();
        }
        // k_UGCQueryHandleInvalid
        if (handle == -1L) {
            return Optional.empty();
        }
        try {
            long callHandle;
            try {
                callHandle = (long) sendQueryUGCRequest.invoke(ugc, handle);
            } catch (Throwable t) {
                return Optional.empty();
            }
            // k_uAPICallInvalid
            if (callHandle == 0L) {
                return Optional.empty();
            }
            pendingApiCalls.add(callHandle);
            long deadline = System.currentTimeMillis() + PUBLISHED_TIMES_TIMEOUT_MILLIS;
            while (!apiCallResults.containsKey(callHandle)) {
                if (System.currentTimeMillis() > deadline) {
                    pendingApiCalls.remove(callHandle);
                    return Optional.empty();
                }
                pumpCallbacks();
                if (apiCallResults.containsKey(callHandle)) {
                    break;
                }
                Thread.sleep(POLL_MILLIS);
            }
            ApiCallResult callResult = apiCallResults.remove(callHandle);
            if (callResult.failed()
                    || callResult.callbackId() != UGC_QUERY_COMPLETED_CALLBACK
                    || callResult.data().byteSize() < 16) {
                return Optional.empty();
            }
            MemorySegment queryCompleted = callResult.data();
            int queryResult = queryCompleted.get(ValueLayout.JAVA_INT_UNALIGNED, 8);
            if (queryResult != RESULT_OK) {
                return Optional.empty();
            }
            int numResults = queryCompleted.get(ValueLayout.JAVA_INT_UNALIGNED, 12);
            long nowEpochSeconds = System.currentTimeMillis() / 1000;
            Map<Long, Long> result = new HashMap<>();
            MemorySegment details = arena.allocate(DETAILS_STRUCT_SIZE);
            for (int index = 0; index < numResults; index++) {
                boolean ok;
                try {
                    ok = (boolean) getQueryUGCResult.invoke(ugc, handle, index, details);
                } catch (Throwable t) {
                    return Optional.empty();
                }
                if (!ok) {
                    return Optional.empty();
                }
                long fileId = detailsPublishedFileId(details);
                long timeUpdated = detailsTimeUpdated(details, packSmall);
                DetailsOutcome outcome =
                        detailsOutcome(
                                detailsResult(details),
                                fileId,
                                timeUpdated,
                                requested,
                                nowEpochSeconds);
                if (outcome == DetailsOutcome.UNSANE) {
                    return Optional.empty();
                }
                if (outcome == DetailsOutcome.TRUSTED) {
                    result.put(fileId, timeUpdated);
                }
            }
            return Optional.of(result);
        } finally {
            try {
                releaseQueryUGCRequest.invoke(ugc, handle);
            } catch (Throwable ignored) {
                // best effort; the request handle only leaks inside Steam's own client process
            }
        }
    }

    /** GetItemInstallInfo's install stamp; empty when the item isn't installed. */
    OptionalLong installTimestamp(long itemId) {
        if (getItemInstallInfo == null) {
            return OptionalLong.empty();
        }
        MemorySegment sizeOnDisk = arena.allocate(ValueLayout.JAVA_LONG);
        MemorySegment folder = arena.allocate(INSTALL_FOLDER_BUFFER_SIZE);
        MemorySegment timestamp = arena.allocate(ValueLayout.JAVA_INT);
        boolean known;
        try {
            known =
                    (boolean)
                            getItemInstallInfo.invoke(
                                    ugc,
                                    itemId,
                                    sizeOnDisk,
                                    folder,
                                    INSTALL_FOLDER_BUFFER_SIZE,
                                    timestamp);
        } catch (Throwable t) {
            throw new SteamException("GetItemInstallInfo(" + itemId + ") failed: " + t);
        }
        if (!known) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Integer.toUnsignedLong(timestamp.get(ValueLayout.JAVA_INT, 0)));
    }

    /**
     * Skips the DownloadItem confirm when the install stamp equals the published stamp and the item
     * is join-ready; otherwise falls through to {@link #updateItem}.
     */
    boolean checkItem(long itemId, Long publishedTimeUpdated, Consumer<String> progress)
            throws InterruptedException {
        if (isCurrentByTimestamp(
                installTimestamp(itemId), publishedTimeUpdated, itemState(itemId))) {
            progress.accept("item " + itemId + " up to date");
            return true;
        }
        return updateItem(itemId, progress);
    }

    static boolean isCurrentByTimestamp(OptionalLong installed, Long published, int state) {
        return published != null
                && installed.isPresent()
                && installed.getAsLong() == published
                && isJoinReady(state);
    }

    /** Verdict on one SteamUGCDetails_t returned by a details query. */
    enum DetailsOutcome {
        /** Non-OK m_eResult (deleted, banned, private to another owner): leave it out. */
        SKIP,
        /** OK and plausible: record its published stamp. */
        TRUSTED,
        /** OK but implausible: a struct-layout mismatch, so the whole batch is discarded. */
        UNSANE
    }

    static DetailsOutcome detailsOutcome(
            int result,
            long publishedFileId,
            long timeUpdated,
            Set<Long> requestedIds,
            long nowEpochSeconds) {
        if (result != RESULT_OK) {
            return DetailsOutcome.SKIP;
        }
        return isSaneDetails(publishedFileId, timeUpdated, requestedIds, nowEpochSeconds)
                ? DetailsOutcome.TRUSTED
                : DetailsOutcome.UNSANE;
    }

    /**
     * Layout check for an OK entry: the file id must be one we asked for and m_rtimeUpdated must
     * fall between 2010 and one day from now.
     */
    static boolean isSaneDetails(
            long publishedFileId, long timeUpdated, Set<Long> requestedIds, long nowEpochSeconds) {
        return requestedIds.contains(publishedFileId)
                && timeUpdated >= EARLIEST_SANE_TIME_UPDATED
                && timeUpdated <= nowEpochSeconds + ONE_DAY_SECONDS;
    }

    /** SteamUGCDetails_t.m_nPublishedFileId, offset 0 on both pack sizes. */
    static long detailsPublishedFileId(MemorySegment details) {
        return details.get(ValueLayout.JAVA_LONG_UNALIGNED, 0);
    }

    /** SteamUGCDetails_t.m_eResult, offset 8 on both pack sizes. */
    static int detailsResult(MemorySegment details) {
        return details.get(ValueLayout.JAVA_INT_UNALIGNED, 8);
    }

    /**
     * SteamUGCDetails_t.m_rtimeUpdated. After m_rgchTitle[129] and m_rgchDescription[8000] the
     * uint64 m_ulSteamIDOwner lands at 8156 (pack 4, linux/macOS) or 8160 (pack 8, Windows), then
     * m_rtimeCreated, then m_rtimeUpdated at 8168 or 8172.
     */
    static long detailsTimeUpdated(MemorySegment details, boolean packSmall) {
        return Integer.toUnsignedLong(
                details.get(ValueLayout.JAVA_INT_UNALIGNED, packSmall ? 8168 : 8172));
    }

    @Override
    public void close() {
        try {
            shutdown.invoke();
        } catch (Throwable ignored) {
            // process exits right after; nothing to recover
        }
        arena.close();
    }
}
