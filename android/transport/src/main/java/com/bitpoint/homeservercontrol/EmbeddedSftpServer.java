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
    static PublicKey authorizedKey(String text) throws Exception {
        return SshPublicKeyParser.parse(text).key;
    }
    static String normalizeAuthorizedKey(String text) throws Exception { return SshPublicKeyParser.parse(text).normalized; }
    synchronized int start(String bind, int port, String user, PublicKey allowed, KeyPair hostKey, Path directory) throws Exception {
        if(server!=null) throw new IllegalStateException("Сервер уже работает");
        if(!ConnectionEndpoint.validUser(user) || port<0 || port>65535 || allowed==null || hostKey==null) throw new IllegalArgumentException("Неверные параметры сервера");
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
            ssh.setPublickeyAuthenticator((name,key,session) -> user.equals(name) && KeyUtils.compareKeys(allowed,key));
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
            ssh.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory.Builder()
                    .withFileSystemAccessor(new SftpFileSystemAccessor() {
                        @Override public void createLink(SftpSubsystemProxy subsystem,Path link,Path target,boolean symbolic) throws IOException {
                            throw new java.nio.file.AccessDeniedException("Links are disabled");
                        }
                    }).build()));
            CoreModuleProperties.AUTH_TIMEOUT.set(ssh,Duration.ofSeconds(20));
            CoreModuleProperties.IDLE_TIMEOUT.set(ssh,Duration.ofMinutes(5));
            CoreModuleProperties.MAX_AUTH_REQUESTS.set(ssh,3);
            CoreModuleProperties.MAX_CONCURRENT_SESSIONS.set(ssh,4);
            CoreModuleProperties.MAX_CONCURRENT_CHANNELS.set(ssh,4);
            ssh.start();server=ssh;return ssh.getPort();
        } catch(Exception e) { ssh.stop(true);throw e; }
    }
    static String fingerprint(KeyPair hostKey) { return KeyUtils.getFingerPrint(hostKey.getPublic()); }
    @Override public synchronized void close() throws IOException { if(server!=null) { SshServer old=server;server=null;old.stop(true); } }
}
