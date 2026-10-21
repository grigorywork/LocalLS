package com.bitpoint.homeservercontrol;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.Collections;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.UserAuthPublicKeyFactory;
import org.apache.sshd.server.forward.RejectAllForwardingFilter;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.file.root.RootedFileSystemProvider;
import org.apache.sshd.common.file.root.RootedPath;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.apache.sshd.sftp.server.SftpFileSystemAccessor;
import org.apache.sshd.sftp.server.SftpSubsystemProxy;

/** SFTP only: no passwords, shell, exec, forwarding or filesystem links. Android API 26+. */
final class EmbeddedSftpServer implements AutoCloseable {
    private SshServer server;
    private volatile java.util.function.Consumer<String> receiver=path -> {};
    void onReceived(java.util.function.Consumer<String> callback){receiver=callback;}
    private void received(Path path){
        try {String relative=path.toString();while(relative.startsWith("/"))relative=relative.substring(1);
            TransferPaths.relative(relative);if(!relative.isEmpty() && !TransferPaths.temporary(relative))receiver.accept(relative);
        }catch(RuntimeException ignored){/* Callback failures must not turn a completed SFTP close into data loss. */}
    }
    private final java.util.concurrent.atomic.AtomicReference<PublicKey> allowedClient=new java.util.concurrent.atomic.AtomicReference<>();
    static PublicKey authorizedKey(String text) throws Exception {
        return SshPublicKeyParser.parse(text).key;
    }
    static String normalizeAuthorizedKey(String text) throws Exception { return SshPublicKeyParser.parse(text).normalized; }
    synchronized int start(String bind,int port,String user,PublicKey allowed,KeyPair hostKey,Path directory) throws Exception {
        if(allowed==null)throw new IllegalArgumentException("Нужен разрешённый ключ");
        return startInternal(bind,port,user,allowed,hostKey,directory);
    }
    /** Explicit locked startup: no password or key can authenticate until local approval. */
    synchronized int startWaiting(String bind,int port,String user,KeyPair hostKey,Path directory) throws Exception {
        return startInternal(bind,port,user,null,hostKey,directory);
    }
    synchronized void authorizeFirstClient(PublicKey key) {
        if(server==null || key==null || !allowedClient.compareAndSet(null,key))
            throw new IllegalStateException("Для замены ключа останови сервер");
    }
    private int startInternal(String bind,int port,String user,PublicKey allowed,KeyPair hostKey,Path directory) throws Exception {
        if(server!=null) throw new IllegalStateException("Сервер уже работает");
        if(!ConnectionEndpoint.validUser(user) || port<0 || port>65535 || hostKey==null) throw new IllegalArgumentException("Неверные параметры сервера");
        allowedClient.set(allowed);
        Files.createDirectories(directory); Path root=directory.toRealPath();
        // Android has no desktop user.name/user.home. Configure SSHD before ServerBuilder's
        // default authorized_keys authenticator is initialized (even though ours replaces it).
        org.apache.sshd.common.util.OsUtils.setCurrentUser(user);
        org.apache.sshd.common.util.io.PathUtils.setUserHomeFolderResolver(() -> root);
        SshServer ssh=SshServer.setUpDefaultServer();
        try {
            ssh.setHost(bind);ssh.setPort(port);
            ssh.setKeyPairProvider(session -> Collections.singletonList(hostKey));
            ssh.setUserAuthFactories(Collections.singletonList(UserAuthPublicKeyFactory.INSTANCE));
            ssh.setPasswordAuthenticator((name,password,session) -> false);
            ssh.setPublickeyAuthenticator((name,key,session) -> {
                PublicKey approved=allowedClient.get();
                return approved!=null && user.equals(name) && KeyUtils.compareKeys(approved,key);
            });
            java.util.Set<org.apache.sshd.common.session.Session> sessions=java.util.concurrent.ConcurrentHashMap.newKeySet();
            ssh.addSessionListener(new org.apache.sshd.common.session.SessionListener() {
                @Override public void sessionCreated(org.apache.sshd.common.session.Session session) {
                    sessions.add(session);if(sessions.size()>8)session.close(true);
                }
                @Override public void sessionClosed(org.apache.sshd.common.session.Session session) { sessions.remove(session); }
            });
            ssh.setShellFactory(null);ssh.setCommandFactory(null);
            ssh.setForwardingFilter(RejectAllForwardingFilter.INSTANCE);
            ssh.setFileSystemFactory(new VirtualFileSystemFactory(root) {
                @Override public FileSystem createFileSystem(SessionContext session) throws IOException {
                    return new RootedFileSystemProvider() {
                        @Override protected Path resolveLocalPath(RootedPath path) {
                            Path result=super.resolveLocalPath(path).normalize();
                            if(!result.startsWith(root)) throw new InvalidPathException(path.toString(),"Outside shared folder");
                            Path check=root;
                            for(Path segment:root.relativize(result)) {
                                check=check.resolve(segment);
                                if(Files.isSymbolicLink(check)) throw new InvalidPathException(path.toString(),"Links are disabled");
                            }
                            return result;
                        }
                    }.newFileSystem(root,Collections.emptyMap());
                }
            });
            // POSIX rename replaces its destination by design. This share preserves existing files.
            org.apache.sshd.sftp.SftpModuleProperties.OPENSSH_EXTENSIONS.set(ssh,
                    "fsync@openssh.com=1,lsetstat@openssh.com=1,limits@openssh.com=1");
            SftpSubsystemFactory factory=new SftpSubsystemFactory.Builder()
                    .withFileSystemAccessor(new SftpFileSystemAccessor() {
                        @Override public void renameFile(SftpSubsystemProxy subsystem,Path source,Path target,java.util.Collection<CopyOption> options) throws IOException {
                            if(options.contains(StandardCopyOption.REPLACE_EXISTING) || options.contains(StandardCopyOption.ATOMIC_MOVE))
                                throw new java.nio.file.AccessDeniedException("Overwrite rename is disabled");
                            Files.move(source,target);
                        }
                        @Override public void createLink(SftpSubsystemProxy subsystem,Path link,Path target,boolean symbolic) throws IOException {
                            throw new java.nio.file.AccessDeniedException("Links are disabled");
                        }
                    }).build();
            factory.addSftpEventListener(new org.apache.sshd.sftp.server.SftpEventListener(){
                private final java.util.Set<org.apache.sshd.sftp.server.Handle> failed=java.util.concurrent.ConcurrentHashMap.newKeySet();
                @Override public void written(org.apache.sshd.server.session.ServerSession session,String handle,org.apache.sshd.sftp.server.FileHandle file,long offset,byte[] data,int off,int len,Throwable error){if(error!=null)failed.add(file);}
                @Override public void closed(org.apache.sshd.server.session.ServerSession session,String handle,org.apache.sshd.sftp.server.Handle file,Throwable error){
                    boolean bad=failed.remove(file);
                    if(error==null && !bad && file instanceof org.apache.sshd.sftp.server.FileHandle){
                        java.util.Set<StandardOpenOption> options=((org.apache.sshd.sftp.server.FileHandle)file).getOpenOptions();
                        if(options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.APPEND))EmbeddedSftpServer.this.received(file.getFile());
                    }
                }
                @Override public void moved(org.apache.sshd.server.session.ServerSession session,Path source,Path target,java.util.Collection<java.nio.file.CopyOption> options,Throwable error){if(error==null && Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))EmbeddedSftpServer.this.received(target);}
            });
            ssh.setSubsystemFactories(Collections.singletonList(factory));
            CoreModuleProperties.AUTH_TIMEOUT.set(ssh,Duration.ofSeconds(20));
            CoreModuleProperties.IDLE_TIMEOUT.set(ssh,Duration.ofMinutes(5));
            CoreModuleProperties.MAX_AUTH_REQUESTS.set(ssh,3);
            CoreModuleProperties.MAX_CONCURRENT_SESSIONS.set(ssh,4);
            CoreModuleProperties.MAX_CONCURRENT_CHANNELS.set(ssh,4);
            ssh.start();server=ssh;return ssh.getPort();
        } catch(Exception e) { ssh.stop(true);throw e; }
    }
    static String fingerprint(KeyPair hostKey) { return KeyUtils.getFingerPrint(hostKey.getPublic()); }
    @Override public synchronized void close() throws IOException { if(server!=null) { SshServer old=server;server=null;old.stop(true);allowedClient.set(null); } }
}
