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
    private static final class DrainingServer extends SshServer {
        void drainAcceptor() throws IOException {
            if(acceptor!=null && isStarted()) {
                // Pre-close outside SSHD's sequential future listener. Otherwise the
                // last socket completion can close the NIO2 factory on its own IO
                // thread, which cannot await termination of its own channel group.
                var closed=acceptor.close(true);
                long deadline=System.nanoTime()+CoreModuleProperties.STOP_WAIT_TIME.getRequired(this).toNanos();
                boolean interrupted=false;
                try{
                    while(!closed.isClosed()){
                        long remaining=deadline-System.nanoTime();
                        if(remaining<=0)throw new java.net.SocketTimeoutException("SFTP listener did not finish closing");
                        try{
                            if(!closed.await(Duration.ofNanos(remaining)))
                                throw new java.net.SocketTimeoutException("SFTP listener did not finish closing");
                        }catch(java.io.InterruptedIOException error){interrupted=true;}
                    }
                }finally{if(interrupted)Thread.currentThread().interrupt();}
            }
        }
    }
    private SshServer server;
    private volatile java.util.function.Supplier<ServerTelemetry> telemetry=()->new ServerTelemetry(-1,-1,-1,-1);
    void telemetry(java.util.function.Supplier<ServerTelemetry> source){telemetry=java.util.Objects.requireNonNull(source);}
    private volatile java.util.function.Consumer<String> receiver=path -> {};
    void onReceived(java.util.function.Consumer<String> callback){receiver=callback;}
    static final class Receipt {
        final String path,key;final long bytes,modified;
        Receipt(String path,String key,long bytes,long modified){this.path=path;this.key=key;this.bytes=bytes;this.modified=modified;}
    }
    private volatile java.util.function.Consumer<Receipt> receiptListener=receipt->{};
    void onReceipt(java.util.function.Consumer<Receipt> listener){receiptListener=java.util.Objects.requireNonNull(listener);}
    synchronized java.util.Set<String> onlineClientFingerprints(){
        java.util.Set<String> online=new java.util.HashSet<>();
        for(var session:sessions){String id=session.getAttribute(AUTH_KEY);if(session.isOpen()&&session.isAuthenticated()&&allowedClients.containsKey(id))online.add(id);}
        return java.util.Collections.unmodifiableSet(online);
    }
    private void received(Path path,org.apache.sshd.server.session.ServerSession session){
        try {String relative=path.toString();while(relative.startsWith("/"))relative=relative.substring(1);
            TransferPaths.relative(relative);if(!relative.isEmpty() && !TransferPaths.temporary(relative)){
                try{String key=session.getAttribute(AUTH_KEY);if(key!=null)receiptListener.accept(new Receipt(relative,key,Files.size(path),Files.getLastModifiedTime(path,LinkOption.NOFOLLOW_LINKS).toMillis()));}catch(Exception ignored){}
                receiver.accept(relative);
            }
        }catch(RuntimeException ignored){/* Callback failures must not turn a completed SFTP close into data loss. */}
    }
    static final int MAX_DEVICES=7,SESSIONS_PER_DEVICE=4;
    static final int HANDLES_PER_CHANNEL=16,HANDLES_PER_DEVICE=32;
    private final java.util.Map<String,PublicKey> allowedClients=new java.util.LinkedHashMap<>();
    private final java.util.Set<org.apache.sshd.common.session.Session> sessions=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final org.apache.sshd.common.AttributeRepository.AttributeKey<String> AUTH_KEY=new org.apache.sshd.common.AttributeRepository.AttributeKey<>();
    private boolean allowed(PublicKey key){PublicKey found=allowedClients.get(KeyUtils.getFingerPrint(key));return found!=null&&KeyUtils.compareKeys(found,key);}
    synchronized void revokeClient(String text)throws Exception{
        String id=KeyUtils.getFingerPrint(authorizedKey(text));allowedClients.remove(id);
        if(pendingPairingPublic!=null&&id.equals(KeyUtils.getFingerPrint(pendingPairingPublic))){revokedInvitations.put(pairingNonce,pairingExpires);pairingNonce="";pendingPairingKey="";pendingPairingPublic=null;pairingApproved=false;}
        for(var session:sessions)if(id.equals(session.getAttribute(AUTH_KEY)))session.close(true);
    }
    private void addAllowed(PublicKey key){
        String id=KeyUtils.getFingerPrint(key);if(allowedClients.containsKey(id)){if(!allowed(key))throw new IllegalStateException("Ключ отличается");return;}
        if(allowedClients.size()>=MAX_DEVICES)throw new IllegalStateException("Разрешено не более 7 устройств");allowedClients.put(id,key);
    }
    /** Keep the live budget bounded without letting idle handshakes starve the next client. */
    private synchronized void admitSession(org.apache.sshd.common.session.Session incoming){
        int limit=MAX_DEVICES*SESSIONS_PER_DEVICE+8;
        if(sessions.stream().filter(s->s.isOpen()).count()>=limit){
            var oldest=sessions.stream().filter(s->s.isOpen()&&!s.isAuthenticated())
                    .min(java.util.Comparator.comparingLong(s->s.getIoSession().getId())).orElse(null);
            if(oldest==null){incoming.close(true);return;}
            // An authenticated file session must never be a flood-eviction candidate.
            oldest.close(true);
        }
        sessions.add(incoming);
    }
    private synchronized boolean authenticate(String name,PublicKey key,org.apache.sshd.server.session.ServerSession session){
        boolean accepted=pairingUser.equals(name)?allowed(key):pairingAuthentication(name,key);
        if(!accepted)return false;
        String id=KeyUtils.getFingerPrint(key);long active=sessions.stream().filter(s->s!=session&&s.isAuthenticated()&&id.equals(s.getAttribute(AUTH_KEY))).count();
        if(active>=SESSIONS_PER_DEVICE)return false;session.setAttribute(AUTH_KEY,id);return true;
    }
    private final java.util.Map<String,Long> revokedInvitations=new java.util.HashMap<>();
    private String pairingNonce="",pairingUser="",pendingPairingKey="";
    private long pairingExpires;
    private PublicKey pendingPairingPublic;
    private boolean pairingApproved;
    synchronized void beginPairing(String nonce,long expires){
        long now=System.currentTimeMillis();revokedInvitations.entrySet().removeIf(e->e.getValue()<=now);
        if(revokedInvitations.containsKey(nonce))throw new IllegalStateException("Доступ отозван. Создай новый QR");
        if(server==null||nonce==null||!nonce.matches("[a-f0-9-]{36}")||expires<=now||expires-now>600000L)throw new IllegalStateException("Приглашение недоступно");
        if(nonce.equals(pairingNonce)&&expires==pairingExpires)return;
        pairingNonce=nonce;pairingExpires=expires;pendingPairingKey="";pendingPairingPublic=null;pairingApproved=false;
    }
    synchronized String pendingPairingKey(String nonce){return activePairing(nonce)?pendingPairingKey:"";}
    synchronized void endPairing(String nonce){if(pairingNonce.equals(nonce)){pairingNonce="";pendingPairingKey="";pendingPairingPublic=null;pairingApproved=false;}}
    private boolean activePairing(String nonce){return server!=null&&!pairingNonce.isEmpty()&&pairingNonce.equals(nonce)&&System.currentTimeMillis()<pairingExpires;}
    synchronized void approvePairing(String nonce,String publicKey)throws Exception{
        if(!activePairing(nonce)||pendingPairingPublic==null||!normalizeAuthorizedKey(publicKey).equals(pendingPairingKey))throw new IllegalStateException("Запрос изменился или истёк");
        addAllowed(pendingPairingPublic);pairingApproved=true;
    }
    synchronized void approveOptical(String nonce,String publicKey)throws Exception{
        if(!activePairing(nonce))throw new IllegalStateException("QR изменился или истёк");
        String normalized=normalizeAuthorizedKey(publicKey);PublicKey key=authorizedKey(normalized);
        if(pendingPairingPublic!=null&&!KeyUtils.compareKeys(pendingPairingPublic,key))throw new IllegalStateException("Запрос другого устройства уже ожидает подтверждения");
        addAllowed(key);pendingPairingPublic=key;pendingPairingKey=normalized;pairingApproved=true;
    }
    private synchronized boolean pairingAuthentication(String name,PublicKey key){
        if(!activePairing(pairingNonce)||!(pairingUser+"#"+pairingNonce).equals(name))return false;
        if(!allowed(key)&&allowedClients.size()>=MAX_DEVICES)return false;
        if(pendingPairingPublic==null)try{
            pendingPairingKey=normalizeAuthorizedKey(org.apache.sshd.common.config.keys.PublicKeyEntry.toString(key));pendingPairingPublic=key;
        }catch(Exception invalid){return false;}
        return pairingApproved&&KeyUtils.compareKeys(pendingPairingPublic,key)&&allowed(key);
    }
    static PublicKey authorizedKey(String text) throws Exception {
        return SshPublicKeyParser.parse(text).key;
    }
    static String normalizeAuthorizedKey(String text) throws Exception { return SshPublicKeyParser.parse(text).normalized; }
    static String publicFingerprint(String text)throws Exception{return KeyUtils.getFingerPrint(authorizedKey(text));}
    synchronized int start(String bind,int port,String user,PublicKey allowed,KeyPair hostKey,Path directory) throws Exception {
        if(allowed==null)throw new IllegalArgumentException("Нужен разрешённый ключ");
        return startInternal(bind,port,user,java.util.Collections.singletonList(allowed),hostKey,directory);
    }
    /** Explicit locked startup: no password or key can authenticate until local approval. */
    synchronized int startWaiting(String bind,int port,String user,KeyPair hostKey,Path directory) throws Exception {
        return startInternal(bind,port,user,java.util.Collections.emptyList(),hostKey,directory);
    }
    synchronized void authorizeFirstClient(PublicKey key) {
        if(server==null || key==null || !allowedClients.isEmpty())throw new IllegalStateException("Первый ключ уже разрешён");
        addAllowed(key);
    }
    synchronized int startAuthorized(String bind,int port,String user,java.util.Collection<PublicKey> allowed,KeyPair hostKey,Path directory)throws Exception{
        return startInternal(bind,port,user,allowed,hostKey,directory);
    }
    private int startInternal(String bind,int port,String user,java.util.Collection<PublicKey> allowed,KeyPair hostKey,Path directory) throws Exception {
        if(server!=null) throw new IllegalStateException("Сервер уже работает");
        if(!ConnectionEndpoint.validUser(user) || port<0 || port>65535 || hostKey==null) throw new IllegalArgumentException("Неверные параметры сервера");
        allowedClients.clear();for(PublicKey key:allowed)addAllowed(key);
        pairingNonce="";pendingPairingKey="";pendingPairingPublic=null;pairingApproved=false;pairingUser=user;
        Files.createDirectories(directory); Path root=directory.toRealPath();
        // Android has no desktop user.name/user.home. Configure SSHD before ServerBuilder's
        // default authorized_keys authenticator is initialized (even though ours replaces it).
        org.apache.sshd.common.util.OsUtils.setCurrentUser(user);
        org.apache.sshd.common.util.io.PathUtils.setUserHomeFolderResolver(() -> root);
        SshServer ssh=org.apache.sshd.server.ServerBuilder.builder().factory(DrainingServer::new).build();
        try {
            ssh.setIoServiceFactoryFactory(new org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory(){
                @Override public org.apache.sshd.common.io.IoServiceFactory create(org.apache.sshd.common.FactoryManager manager){
                    var pool=org.apache.sshd.common.util.threads.ThreadUtils.newFixedThreadPool(
                            manager+"-nio2",org.apache.sshd.common.io.AbstractIoServiceFactory.getNioWorkers(manager));
                    return new org.apache.sshd.common.io.nio2.Nio2ServiceFactory(manager,new NioCompletionExecutor(pool),null);
                }
            });
            ssh.setHost(bind);ssh.setPort(port);
            // Keep the stored RSA identity; use RSA/SHA-2 rather than legacy SHA-1
            // signatures for both host negotiation and public-key authentication.
            ssh.setSignatureFactories(ssh.getSignatureFactories().stream().filter(factory ->
                    !factory.getName().equals("ssh-rsa") && !factory.getName().equals("ssh-rsa-cert-v01@openssh.com")
                    && !factory.getName().startsWith("ssh-dss")).collect(java.util.stream.Collectors.toList()));
            ssh.setKeyPairProvider(session -> Collections.singletonList(hostKey));
            ssh.setUserAuthFactories(Collections.singletonList(UserAuthPublicKeyFactory.INSTANCE));
            ssh.setPasswordAuthenticator((name,password,session) -> false);
            ssh.setPublickeyAuthenticator((name,key,session) -> authenticate(name,key,session));
            ssh.addSessionListener(new org.apache.sshd.common.session.SessionListener() {
                @Override public void sessionCreated(org.apache.sshd.common.session.Session session) {
                    admitSession(session);
                }
                @Override public void sessionEvent(org.apache.sshd.common.session.Session session,org.apache.sshd.common.session.SessionListener.Event event){
                    if(event!=org.apache.sshd.common.session.SessionListener.Event.Authenticated)return;
                    synchronized(EmbeddedSftpServer.this){String id=session.getAttribute(AUTH_KEY);
                        long count=sessions.stream().filter(s->s.isAuthenticated()&&java.util.Objects.equals(id,s.getAttribute(AUTH_KEY))).count();
                        if(id==null||!allowedClients.containsKey(id)||count>SESSIONS_PER_DEVICE)session.close(true);
                    }
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
            SftpSubsystemFactory factory=new SftpSubsystemFactory(){
                @Override public org.apache.sshd.server.command.Command createSubsystem(org.apache.sshd.server.channel.ChannelSession channel){
                    BoundedSftpSubsystem subsystem=new BoundedSftpSubsystem(channel,this);
                    for(var listener:getRegisteredListeners())subsystem.addSftpEventListener(listener);
                    return subsystem;
                }
            };
            org.apache.sshd.sftp.SftpModuleProperties.MAX_OPEN_HANDLES_PER_SESSION.set(ssh,HANDLES_PER_CHANNEL);
            // Budget by authenticated key, across all channels and sessions. Reserve before
            // opening so a rejected CREATE/TRUNCATE cannot alter the user's existing file.
            factory.setFileSystemAccessor(new SftpFileSystemAccessor() {
                        private final java.util.Map<org.apache.sshd.sftp.server.Handle,String> handles=new java.util.IdentityHashMap<>();
                        private synchronized void reserve(SftpSubsystemProxy subsystem,org.apache.sshd.sftp.server.Handle handle)throws IOException{
                            // Internal temporary channels have no remote handle; the bounded
                            // subsystem worker opens/closes them within a single operation.
                            if(handle==null)return;
                            String owner=subsystem.getServerSession().getAttribute(AUTH_KEY);
                            if(owner==null||handles.values().stream().filter(owner::equals).count()>=HANDLES_PER_DEVICE)
                                throw new IOException("Too many open files for this device");
                            handles.put(handle,owner);
                        }
                        private synchronized void release(org.apache.sshd.sftp.server.Handle handle){handles.remove(handle);}
                        @Override public java.nio.channels.SeekableByteChannel openFile(SftpSubsystemProxy subsystem,org.apache.sshd.sftp.server.FileHandle handle,Path file,String id,java.util.Set<? extends OpenOption> options,java.nio.file.attribute.FileAttribute<?>... attrs)throws IOException{
                            reserve(subsystem,handle);
                            try{return SftpFileSystemAccessor.super.openFile(subsystem,handle,file,id,options,attrs);}
                            catch(IOException|RuntimeException|Error error){release(handle);throw error;}
                        }
                        @Override public void closeFile(SftpSubsystemProxy subsystem,org.apache.sshd.sftp.server.FileHandle handle,Path file,String id,java.nio.channels.Channel channel,java.util.Set<? extends OpenOption> options)throws IOException{
                            try{SftpFileSystemAccessor.super.closeFile(subsystem,handle,file,id,channel,options);}
                            finally{release(handle);}
                        }
                        @Override public DirectoryStream<Path> openDirectory(SftpSubsystemProxy subsystem,org.apache.sshd.sftp.server.DirectoryHandle handle,Path file,String id,LinkOption... options)throws IOException{
                            reserve(subsystem,handle);
                            try{return SftpFileSystemAccessor.super.openDirectory(subsystem,handle,file,id,options);}
                            catch(IOException|RuntimeException|Error error){release(handle);throw error;}
                        }
                        @Override public void closeDirectory(SftpSubsystemProxy subsystem,org.apache.sshd.sftp.server.DirectoryHandle handle,Path file,String id,DirectoryStream<Path> stream)throws IOException{
                            try{SftpFileSystemAccessor.super.closeDirectory(subsystem,handle,file,id,stream);}
                            finally{release(handle);}
                        }
                        @Override public void renameFile(SftpSubsystemProxy subsystem,Path source,Path target,java.util.Collection<CopyOption> options) throws IOException {
                            if(options.contains(StandardCopyOption.REPLACE_EXISTING) || options.contains(StandardCopyOption.ATOMIC_MOVE))
                                throw new java.nio.file.AccessDeniedException("Overwrite rename is disabled");
                            Files.move(source,target);
                        }
                        @Override public void createLink(SftpSubsystemProxy subsystem,Path link,Path target,boolean symbolic) throws IOException {
                            throw new java.nio.file.AccessDeniedException("Links are disabled");
                        }
                    });
            factory.addSftpEventListener(new org.apache.sshd.sftp.server.SftpEventListener(){
                private final java.util.Set<org.apache.sshd.sftp.server.Handle> failed=java.util.concurrent.ConcurrentHashMap.newKeySet();
                @Override public void written(org.apache.sshd.server.session.ServerSession session,String handle,org.apache.sshd.sftp.server.FileHandle file,long offset,byte[] data,int off,int len,Throwable error){if(error!=null)failed.add(file);}
                @Override public void closed(org.apache.sshd.server.session.ServerSession session,String handle,org.apache.sshd.sftp.server.Handle file,Throwable error){
                    boolean bad=failed.remove(file);
                    if(error==null && !bad && file instanceof org.apache.sshd.sftp.server.FileHandle){
                        java.util.Set<StandardOpenOption> options=((org.apache.sshd.sftp.server.FileHandle)file).getOpenOptions();
                        if(options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.APPEND))EmbeddedSftpServer.this.received(file.getFile(),session);
                    }
                }
                @Override public void moved(org.apache.sshd.server.session.ServerSession session,Path source,Path target,java.util.Collection<java.nio.file.CopyOption> options,Throwable error){if(error==null && Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))EmbeddedSftpServer.this.received(target,session);}
            });
            ssh.setSubsystemFactories(java.util.Arrays.asList(factory,new StatusSubsystem(()->telemetry.get()),new StatusSubsystem(()->telemetry.get(),"localls-status-v2"),new StatusSubsystem(()->telemetry.get(),"localls-status-v3")));
            CoreModuleProperties.AUTH_TIMEOUT.set(ssh,Duration.ofSeconds(20));
            CoreModuleProperties.IDLE_TIMEOUT.set(ssh,Duration.ofMinutes(5));
            CoreModuleProperties.MAX_AUTH_REQUESTS.set(ssh,3);
            CoreModuleProperties.MAX_CONCURRENT_SESSIONS.set(ssh,MAX_DEVICES*SESSIONS_PER_DEVICE);
            CoreModuleProperties.MAX_CONCURRENT_CHANNELS.set(ssh,4);
            ssh.start();server=ssh;return ssh.getPort();
        } catch(Exception e) { ssh.stop(true);throw e; }
    }
    static String fingerprint(KeyPair hostKey) { return KeyUtils.getFingerPrint(hostKey.getPublic()); }
    @Override public void close() throws IOException {
        SshServer old;synchronized(this){old=server;server=null;pairingNonce="";pendingPairingKey="";pendingPairingPublic=null;pairingApproved=false;allowedClients.clear();}
        if(old!=null){
            // NIO2 must drain its channel group before its executor is stopped. An
            // inherited interrupt makes SSHD skip that wait and can strand callbacks.
            boolean interrupted=Thread.interrupted();
            try{
                try{((DrainingServer)old).drainAcceptor();}
                finally{interrupted|=Thread.interrupted();old.stop(true);}
            }
            catch(java.io.InterruptedIOException error){interrupted=true;throw error;}
            finally{if(interrupted)Thread.currentThread().interrupt();}
        }
    }
}
