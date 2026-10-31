package com.bitpoint.homeservercontrol;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.sshd.common.util.threads.ThreadUtils;
import org.junit.Test;
import static org.junit.Assert.*;

public class NioCompletionExecutorTest {
    @Test public void queuedAndLateCloseCompletionsRunExactlyOnceAndWorkersActuallyTerminate()throws Exception{
        var pool=ThreadUtils.newFixedThreadPool("disposable-completion-test",1);
        var executor=new NioCompletionExecutor(pool);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicInteger queued=new AtomicInteger(),late=new AtomicInteger();
        try{
            executor.execute(()->{entered.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}});
            assertTrue(entered.await(5,TimeUnit.SECONDS));executor.execute(queued::incrementAndGet);
            executor.shutdown();assertTrue(executor.isShutdown());
            assertFalse("A logical close is not worker termination",executor.isTerminated());
            assertFalse("awaitTermination must wait for the real worker",executor.awaitTermination(10,TimeUnit.MILLISECONDS));
            executor.execute(late::incrementAndGet);assertEquals(1,late.get());
            release.countDown();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));
            assertEquals(1,queued.get());assertEquals(1,late.get());assertTrue(executor.isTerminated());assertTrue(pool.isTerminated());
        }finally{release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
    }
}
