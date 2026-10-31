package com.bitpoint.homeservercontrol;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.util.threads.CloseableExecutorService;
import org.apache.sshd.common.util.threads.NoCloseExecutor;

/** The NIO2 group owns this pool; late channel-close completions must still run. */
final class NioCompletionExecutor extends NoCloseExecutor {
    private final CloseableExecutorService pool;
    NioCompletionExecutor(CloseableExecutorService pool){super(pool);this.pool=pool;}

    @Override public void execute(Runnable completion){
        Objects.requireNonNull(completion);
        synchronized(this){
            // Serialize with close: SSHD's underlying CallerRunsPolicy silently
            // discards a rejected task once its pool is shut down.
            if(!isShutdown()){pool.execute(completion);return;}
        }
        // A channel group can dispatch a final read/accept completion from its
        // internal poller after requesting executor shutdown. Drain that callback
        // on the caller, rather than dropping it or throwing NoCloseExecutor's
        // IllegalStateException out of the poller. Session admission is closed.
        completion.run();
    }
    @Override public synchronized CloseFuture close(boolean immediately){
        CloseFuture closed=super.close(immediately);
        // Preserve queued channel completions. The acceptor has already closed its
        // sessions; the group sends termination wakeups before shutting this pool.
        pool.shutdown();
        return closed;
    }
    @Override public boolean isTerminated(){return pool.isTerminated();}
    @Override public boolean awaitTermination(long timeout,TimeUnit unit)throws InterruptedException{
        // NoCloseExecutor's logical close future is not proof its workers drained.
        return pool.awaitTermination(timeout,unit);
    }
}
