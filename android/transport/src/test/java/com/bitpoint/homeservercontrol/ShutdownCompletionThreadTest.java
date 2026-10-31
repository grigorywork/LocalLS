package com.bitpoint.homeservercontrol;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.sshd.common.future.DefaultCloseFuture;
import org.apache.sshd.common.io.*;
import org.apache.sshd.common.helpers.AbstractFactoryManager;
import org.apache.sshd.server.SshServer;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ShutdownCompletionThreadTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static Object invoke(Method method,Object target,Object[] args)throws Throwable{
        try{return method.invoke(target,args);}catch(InvocationTargetException error){throw error.getCause();}
    }
    @Test public void ioFactoryShutdownRunsAfterAcceptorCompletionOnTheClosingCaller()throws Exception{checkShutdown(false);}
    @Test public void interruptionWhileWaitingForAcceptorIsPreservedWithoutAbandoningShutdown()throws Exception{checkShutdown(true);}
    private void checkShutdown(boolean interruptDuringWait)throws Exception{
        InterruptedShutdownTest.keys();
        var keyField=InterruptedShutdownTest.class.getDeclaredField("host");keyField.setAccessible(true);
        var host=(java.security.KeyPair)keyField.get(null);
        Path root=temp.newFolder().toPath();Files.write(root.resolve("canary"),new byte[]{4,8});
        EmbeddedSftpServer server=new EmbeddedSftpServer();Thread caller=Thread.currentThread();
        AtomicReference<Thread> factoryThread=new AtomicReference<>(),completionThread=new AtomicReference<>();
        AtomicReference<Throwable> completionFailure=new AtomicReference<>();
        try{
            int port=server.startWaiting("127.0.0.1",0,"localls",host,root);
            Field sf=EmbeddedSftpServer.class.getDeclaredField("server");sf.setAccessible(true);SshServer ssh=(SshServer)sf.get(server);
            Field af=SshServer.class.getDeclaredField("acceptor");af.setAccessible(true);IoAcceptor acceptor=(IoAcceptor)af.get(ssh);
            DefaultCloseFuture delayed=new DefaultCloseFuture(null,null);
            IoAcceptor wrapped=(IoAcceptor)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{IoAcceptor.class},(proxy,method,args)->{
                if(method.getName().equals("close")&&method.getParameterCount()==1){
                    var real=acceptor.close((Boolean)args[0]);
                    if(completionThread.get()==null){
                        Thread thread=new Thread(()->{try{
                            assertTrue(real.await(5000));long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                            while(caller.getState()!=Thread.State.WAITING&&caller.getState()!=Thread.State.TIMED_WAITING&&System.nanoTime()<deadline)Thread.yield();
                            assertTrue("Caller never awaited acceptor completion",System.nanoTime()<deadline);
                            if(interruptDuringWait){
                                caller.interrupt();
                                while(caller.isInterrupted()&&System.nanoTime()<deadline)Thread.yield();
                                assertTrue("Interrupt was never observed by the wait",System.nanoTime()<deadline);
                            }
                            delayed.setClosed();
                        }catch(Throwable error){completionFailure.set(error);delayed.setClosed();}},"disposable-io-completion");
                        completionThread.set(thread);thread.start();
                    }
                    return delayed;
                }
                return invoke(method,acceptor,args);
            });af.set(ssh,wrapped);
            Field ff=AbstractFactoryManager.class.getDeclaredField("ioServiceFactory");ff.setAccessible(true);IoServiceFactory factory=(IoServiceFactory)ff.get(ssh);
            ff.set(ssh,Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{IoServiceFactory.class},(proxy,method,args)->{
                if(method.getName().equals("close")&&method.getParameterCount()==1)factoryThread.set(Thread.currentThread());
                return invoke(method,factory,args);
            }));
            boolean interrupted;
            try{server.close();interrupted=Thread.currentThread().isInterrupted();}finally{Thread.interrupted();}
            assertEquals("Closing lost or invented interruption",interruptDuringWait,interrupted);
            completionThread.get().join(5000);assertFalse(completionThread.get().isAlive());assertNull(completionFailure.get());
            assertSame("SSHD shutdown continued on an IO completion thread, which cannot drain its own NIO2 group",caller,factoryThread.get());
            assertArrayEquals(new byte[]{4,8},Files.readAllBytes(root.resolve("canary")));
            try(EmbeddedSftpServer next=new EmbeddedSftpServer()){assertEquals(port,next.startWaiting("127.0.0.1",port,"localls",host,root));}
        }finally{server.close();if(completionThread.get()!=null)completionThread.get().join(5000);}
    }
}
