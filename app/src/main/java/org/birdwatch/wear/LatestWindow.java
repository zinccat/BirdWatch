package org.birdwatch.wear;

/** One pending window: slow inference replaces stale work instead of building a backlog. */
public final class LatestWindow<T> {
    private T pending;
    private boolean closed;
    private Exception failure;
    public synchronized void offer(T item) { if (!closed) { pending = item; notifyAll(); } }
    public synchronized T take() throws Exception {
        while (pending == null && !closed) wait();
        if (failure != null) throw failure;
        if (closed) return null;
        T item = pending; pending = null; return item;
    }
    public synchronized void close() { closed = true; pending = null; notifyAll(); }
    public synchronized void fail(Exception error) { failure = error; close(); }
    public synchronized void checkFailure() throws Exception { if (failure != null) throw failure; }
}
