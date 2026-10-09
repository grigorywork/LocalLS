package com.bitpoint.homeservercontrol;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpProgressMonitor;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

final class SftpClient {
    interface ProgressCallback {
        /**
         * @return true to continue, false to cancel the transfer.
         */
        boolean onProgress(long transferredBytes, long totalBytes);
    }

    private SftpClient() {}

    static List<RemoteEntry> list(String host, int port, String user, String password, String trustedFingerprint, String requestedPath) throws Exception { return list(host, port, user, SshCredentials.password(password), trustedFingerprint, requestedPath); }
    static List<RemoteEntry> list(android.content.Context context, String host, int port, String user, String password, String trustedFingerprint, String requestedPath) throws Exception { return list(host, port, user, SshKeys.credentials(context, host, port, user, password), trustedFingerprint, requestedPath); }
    static String canonicalPath(String host, int port, String user, String password, String trustedFingerprint, String path) throws Exception { return canonicalPath(host, port, user, SshCredentials.password(password), trustedFingerprint, path); }
    static String canonicalPath(android.content.Context context, String host, int port, String user, String password, String trustedFingerprint, String path) throws Exception { return canonicalPath(host, port, user, SshKeys.credentials(context, host, port, user, password), trustedFingerprint, path); }
    static void download(String host, int port, String user, String password, String trustedFingerprint, String remotePath, OutputStream output, long expectedBytes, ProgressCallback callback) throws Exception { download(host, port, user, SshCredentials.password(password), trustedFingerprint, remotePath, output, expectedBytes, callback); }
    static void download(android.content.Context context, String host, int port, String user, String password, String trustedFingerprint, String remotePath, OutputStream output, long expectedBytes, ProgressCallback callback) throws Exception { download(host, port, user, SshKeys.credentials(context, host, port, user, password), trustedFingerprint, remotePath, output, expectedBytes, callback); }
    static void upload(String host, int port, String user, String password, String trustedFingerprint, InputStream input, String remotePath, long expectedBytes, ProgressCallback callback) throws Exception { upload(host, port, user, SshCredentials.password(password), trustedFingerprint, input, remotePath, expectedBytes, callback); }
    static void upload(android.content.Context context, String host, int port, String user, String password, String trustedFingerprint, InputStream input, String remotePath, long expectedBytes, ProgressCallback callback) throws Exception { upload(host, port, user, SshKeys.credentials(context, host, port, user, password), trustedFingerprint, input, remotePath, expectedBytes, callback); }
    static void mkdir(String host, int port, String user, String password, String trustedFingerprint, String path) throws Exception { mkdir(host, port, user, SshCredentials.password(password), trustedFingerprint, path); }
    static void mkdir(android.content.Context context, String host, int port, String user, String password, String trustedFingerprint, String path) throws Exception { mkdir(host, port, user, SshKeys.credentials(context, host, port, user, password), trustedFingerprint, path); }
    static void rename(String host, int port, String user, String password, String trustedFingerprint, String oldPath, String newPath) throws Exception { rename(host, port, user, SshCredentials.password(password), trustedFingerprint, oldPath, newPath); }
    static void rename(android.content.Context context, String host, int port, String user, String password, String trustedFingerprint, String oldPath, String newPath) throws Exception { rename(host, port, user, SshKeys.credentials(context, host, port, user, password), trustedFingerprint, oldPath, newPath); }
    static void delete(String host, int port, String user, String password, String trustedFingerprint, RemoteEntry entry) throws Exception { delete(host, port, user, SshCredentials.password(password), trustedFingerprint, entry); }
    static void delete(android.content.Context context, String host, int port, String user, String password, String trustedFingerprint, RemoteEntry entry) throws Exception { delete(host, port, user, SshKeys.credentials(context, host, port, user, password), trustedFingerprint, entry); }

    static List<RemoteEntry> list(String host, int port, String user, SshCredentials credential,
                                  String trustedFingerprint, String requestedPath) throws Exception {
        Connection connection = connect(host, port, user, credential, trustedFingerprint);
        try {
            ChannelSftp sftp = connection.sftp;
            String path = requestedPath == null || requestedPath.trim().isEmpty()
                    ? ServerConfig.DEFAULT_REMOTE_PATH : requestedPath;
            sftp.cd(path);
            String actualPath = sftp.pwd();

            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> raw = sftp.ls(".");
            List<RemoteEntry> out = new ArrayList<>();
            for (ChannelSftp.LsEntry entry : raw) {
                String name = entry.getFilename();
                if (".".equals(name) || "..".equals(name)) continue;
                SftpATTRS attrs = entry.getAttrs();
                out.add(new RemoteEntry(
                        name,
                        join(actualPath, name),
                        attrs != null && attrs.isDir(),
                        attrs == null ? 0L : attrs.getSize(),
                        attrs == null ? 0 : attrs.getMTime()));
            }
            Collections.sort(out, new Comparator<RemoteEntry>() {
                @Override public int compare(RemoteEntry a, RemoteEntry b) {
                    if (a.directory != b.directory) return a.directory ? -1 : 1;
                    return a.name.compareToIgnoreCase(b.name);
                }
            });
            return out;
        } finally {
            connection.close();
        }
    }

    static String canonicalPath(String host, int port, String user, SshCredentials credential,
                                String trustedFingerprint, String path) throws Exception {
        Connection connection = connect(host, port, user, credential, trustedFingerprint);
        try {
            connection.sftp.cd(path);
            return connection.sftp.pwd();
        } finally {
            connection.close();
        }
    }

    static void download(String host, int port, String user, SshCredentials credential,
                         String trustedFingerprint, String remotePath, OutputStream output,
                         long expectedBytes, ProgressCallback callback) throws Exception {
        Connection connection = connect(host, port, user, credential, trustedFingerprint);
        try {
            connection.sftp.get(remotePath, output, monitor(callback, expectedBytes));
            output.flush();
        } finally {
            connection.close();
        }
    }

    static void upload(String host, int port, String user, SshCredentials credential,
                       String trustedFingerprint, InputStream input, String remotePath,
                       long expectedBytes, ProgressCallback callback) throws Exception {
        Connection connection = connect(host, port, user, credential, trustedFingerprint);
        try {
            connection.sftp.put(input, remotePath, monitor(callback, expectedBytes), ChannelSftp.OVERWRITE);
        } finally {
            connection.close();
        }
    }

    static void mkdir(String host, int port, String user, SshCredentials credential,
                      String trustedFingerprint, String path) throws Exception {
        Connection connection = connect(host, port, user, credential, trustedFingerprint);
        try {
            connection.sftp.mkdir(path);
        } finally {
            connection.close();
        }
    }

    static void rename(String host, int port, String user, SshCredentials credential,
                       String trustedFingerprint, String oldPath, String newPath) throws Exception {
        Connection connection = connect(host, port, user, credential, trustedFingerprint);
        try {
            connection.sftp.rename(oldPath, newPath);
        } finally {
            connection.close();
        }
    }

    static void delete(String host, int port, String user, SshCredentials credential,
                       String trustedFingerprint, RemoteEntry entry) throws Exception {
        Connection connection = connect(host, port, user, credential, trustedFingerprint);
        try {
            if (entry.directory) {
                // Защитное поведение: удаляем только пустую папку. Рекурсивное удаление специально не делаем.
                connection.sftp.rmdir(entry.path);
            } else {
                connection.sftp.rm(entry.path);
            }
        } finally {
            connection.close();
        }
    }

    static void installPublicKey(String host,int port,String user,SshCredentials credential,String pin,String publicKey) throws Exception {
        if (!publicKey.matches("(?:ssh-rsa|ssh-ed25519|ecdsa-sha2-nistp(?:256|384|521)) [A-Za-z0-9+/=]+ LocalLS")) throw new Exception("Ключ не распознан");
        Connection connection=connect(host,port,user,credential,pin);
        try { ChannelSftp s=connection.sftp; String home=s.getHome(),dir=join(home,".ssh"),file=join(dir,"authorized_keys"); SftpATTRS attrs;
            try { attrs=s.lstat(dir); if(!attrs.isDir()||attrs.isLink())throw new Exception("Небезопасная папка .ssh"); }
            catch(com.jcraft.jsch.SftpException e){if(e.id!=ChannelSftp.SSH_FX_NO_SUCH_FILE)throw e;s.mkdir(dir);} s.chmod(0700,dir);
            byte[] old=new byte[0];
            try { attrs=s.lstat(file); if(!attrs.isReg()||attrs.isLink()||attrs.getSize()>512*1024)throw new Exception("Небезопасный authorized_keys");
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();try(InputStream in=s.get(file)){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>512*1024)throw new Exception("authorized_keys слишком большой");out.write(b,0,n);}}old=out.toByteArray();
            }catch(com.jcraft.jsch.SftpException e){if(e.id!=ChannelSftp.SSH_FX_NO_SUCH_FILE)throw e;}
            String text=new String(old,java.nio.charset.StandardCharsets.UTF_8); String[] expected=publicKey.split(" "); boolean exists=false;
            for(String line:text.split("\\r?\\n")){String[] parts=line.trim().split("\\s+");if(!line.trim().startsWith("#"))for(int i=0;i+1<parts.length;i++)if(parts[i].equals(expected[0])&&parts[i+1].equals(expected[1]))exists=true;}
            if(!exists){String addition=(!text.isEmpty()&&!text.endsWith("\n")?"\n":"")+publicKey+"\n";try(OutputStream out=s.put(file,ChannelSftp.APPEND)){out.write(addition.getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
            s.chmod(0600,file);
        }finally{connection.close();}
    }

    static String parent(String path) {
        if (path == null || path.trim().isEmpty() || "/".equals(path)) return "/";
        String clean = path.endsWith("/") && path.length() > 1
                ? path.substring(0, path.length() - 1) : path;
        int index = clean.lastIndexOf('/');
        if (index <= 0) return "/";
        return clean.substring(0, index);
    }

    static String join(String parent, String name) {
        if (parent == null || parent.isEmpty() || "/".equals(parent)) return "/" + name;
        return parent.endsWith("/") ? parent + name : parent + "/" + name;
    }

    private static SftpProgressMonitor monitor(final ProgressCallback callback, final long expectedBytes) {
        return new SftpProgressMonitor() {
            private long transferred;
            private long total = expectedBytes;

            @Override public void init(int op, String src, String dest, long max) {
                transferred = 0L;
                if (total <= 0L && max > 0L) total = max;
            }

            @Override public boolean count(long count) {
                transferred += count;
                if (callback == null) return !Thread.currentThread().isInterrupted();
                return callback.onProgress(transferred, total) && !Thread.currentThread().isInterrupted();
            }

            @Override public void end() {
                if (callback != null) callback.onProgress(transferred, total);
            }
        };
    }

    private static Connection connect(String host, int port, String user, SshCredentials credential,
                                      String trustedFingerprint) throws Exception {
        if (trustedFingerprint == null || trustedFingerprint.trim().isEmpty()) {
            throw new Exception("Сначала подтверди SSH fingerprint на главном экране.");
        }
        JSch jsch = new JSch();
        // Enforce the trusted key during key exchange, before SSH password authentication.
        jsch.setHostKeyRepository(new PinnedHostKeyRepository(jsch, trustedFingerprint));
        Session session = jsch.getSession(user, host, port);
        credential.configure(jsch, session);
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "yes");

        session.setConfig(config);
        // Keepalive помогает при длинных передачах и выключенном экране клиента.
        session.setServerAliveInterval(15_000);
        session.setServerAliveCountMax(3);
        session.setTimeout(15_000);
        try {
            session.connect(6000);
    
            HostKey hostKey = session.getHostKey();
            String fingerprint = hostKey == null ? "" : hostKey.getFingerPrint(jsch);
            if (!trustedFingerprint.equals(fingerprint)) {
                session.disconnect();
                throw new Exception("SSH fingerprint изменился. Передача заблокирована.");
            }
    
            ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(6000);
            return new Connection(session, sftp);
        } catch (Exception e) {
            session.disconnect();
            throw e;
        }
    }

    private static final class Connection {
        final Session session;
        final ChannelSftp sftp;

        Connection(Session session, ChannelSftp sftp) {
            this.session = session;
            this.sftp = sftp;
        }

        void close() {
            if (sftp != null && sftp.isConnected()) sftp.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }
}
