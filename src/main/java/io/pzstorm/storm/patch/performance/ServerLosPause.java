package io.pzstorm.storm.patch.performance;

/** Owns one lazy LOS suspension across ordinary unloads and nested warm-cell eviction. */
final class ServerLosPause implements AutoCloseable {
    private final Runnable suspend;
    private final Runnable resume;
    private boolean acquired;

    ServerLosPause(Runnable suspend, Runnable resume) {
        this.suspend = suspend;
        this.resume = resume;
    }

    void acquire() {
        if (!acquired) {
            suspend.run();
            acquired = true;
        }
    }

    @Override
    public void close() {
        if (acquired) {
            acquired = false;
            resume.run();
        }
    }
}
