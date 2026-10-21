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
        return browse(host, port, user, credential, trustedFingerprint, requestedPath).entries;
    }

    static boolean isDirectoryError(Exception error) {
        if (!(error instanceof com.jcraft.jsch.SftpException)) return false;
        int code = ((com.jcraft.jsch.SftpException) error).id;
        return code == ChannelSftp.SSH_FX_NO_SUCH_FILE || code == ChannelSftp.SSH_FX_PERMISSION_DENIED;
    }

    static final class DirectoryListing {
        final String path;
        final List<RemoteEntry> entries;
        DirectoryListing(String path, List<RemoteEntry> entries) {
            this.path = path;
            this.entries = entries;
        }
    }

    static DirectoryListing browse(String host, int port, String user, SshCredentials credential,
                                   String trustedFingerprint, String requestedPath) throws Exception {
        Connection connection = connect(host, port, user, credential, trustedFingerprint);
        try {
            ChannelSftp sftp = connection.sftp;
            String path = requestedPath == null || requestedPath.trim().isEmpty()
                    ? ServerConfig.DEFAULT_REMOTE_PATH : requestedPath;
            sftp.cd(literal(path));
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
            return new DirectoryListing(actualPath, out);
        } finally {
            connection.close();
        }
    }

    static String canonicalPath(String host, int port, String user, SshCredentials credential,
                                String trustedFingerprint, String path) throws Exception {
        Connection connection = connect(host, port, user, credential, trustedFingerprint);
        try {
            connection.sftp.cd(literal(path));
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
            connection.sftp.get(literal(remotePath), output, monitor(callback, expectedBytes));
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
            connection.sftp.put(input, literal(remotePath), monitor(callback, expectedBytes), ChannelSftp.OVERWRITE);
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
            connection.sftp.rename(literal(oldPath), literal(newPath));
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
                connection.sftp.rmdir(literal(entry.path));
            } else {
                connection.sftp.rm(literal(entry.path));
            }
        } finally {
            connection.close();
        }
    }

    static void installPublicKey(String host,int port,String user,SshCredentials credential,String pin,String publicKey) throws Exception {
        if (!publicKey.matches("(?:ssh-rsa|ssh-ed25519|ecdsa-sha2-nistp(?:256|384|521)) [A-Za-z0-9+/=]+ LocalLS")) throw new Exception("Ключ не распознан");
        Connection connection=connect(host,port,user,credential,pin);
        try { ChannelSftp s=connection.sftp; String home=s.getHome(),dir=join(home,".ssh"),file=join(dir,"authorized_keys"); SftpATTRS attrs;
            try { attrs=s.lstat(literal(dir)); if(!attrs.isDir()||attrs.isLink())throw new Exception("Небезопасная папка .ssh"); }
            catch(com.jcraft.jsch.SftpException e){if(e.id!=ChannelSftp.SSH_FX_NO_SUCH_FILE)throw e;s.mkdir(dir);} s.chmod(0700,literal(dir));
            byte[] old=new byte[0];
            try { attrs=s.lstat(literal(file)); if(!attrs.isReg()||attrs.isLink()||attrs.getSize()>512*1024)throw new Exception("Небезопасный authorized_keys");
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();try(InputStream in=s.get(literal(file))){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>512*1024)throw new Exception("authorized_keys слишком большой");out.write(b,0,n);}}old=out.toByteArray();
            }catch(com.jcraft.jsch.SftpException e){if(e.id!=ChannelSftp.SSH_FX_NO_SUCH_FILE)throw e;}
            String text=new String(old,java.nio.charset.StandardCharsets.UTF_8); String[] expected=publicKey.split(" "); boolean exists=false;
            for(String line:text.split("\\r?\\n")){String[] parts=line.trim().split("\\s+");if(!line.trim().startsWith("#"))for(int i=0;i+1<parts.length;i++)if(parts[i].equals(expected[0])&&parts[i+1].equals(expected[1]))exists=true;}
            if(!exists){String addition=(!text.isEmpty()&&!text.endsWith("\n")?"\n":"")+publicKey+"\n";try(OutputStream out=s.put(literal(file),ChannelSftp.APPEND)){out.write(addition.getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
            s.chmod(0600,literal(file));
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

    /** JSch treats remote names as glob patterns even for rm/rmdir. UI paths are always literal. */
    private static String literal(String path) {
        return path.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?");
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


    /** One pinned connection per tree, bounded recursion is enforced by the caller. */
    static final class TreeSession implements AutoCloseable {
        private final Connection c;
        TreeSession(String host,int port,String user,SshCredentials key,String pin)throws Exception{c=connect(host,port,user,key,pin);}
        List<RemoteEntry> list(String path)throws Exception{
            SftpATTRS parent=c.sftp.lstat(literal(path));if(parent.isLink() || !parent.isDir())throw new java.io.IOException("Папка недоступна");
            List<RemoteEntry> out=new ArrayList<>();
            @SuppressWarnings("unchecked") Vector<ChannelSftp.LsEntry> raw=c.sftp.ls(literal(path));
            for(ChannelSftp.LsEntry e:raw){String name=e.getFilename();if(name.equals(".")||name.equals("..")||TransferPaths.temporary(name))continue;
                TransferPaths.name(name);SftpATTRS a=e.getAttrs();if(a==null || a.isLink() || (!a.isDir() && !a.isReg()))continue;
                if(out.size()>=10000)throw new java.io.IOException("Слишком много файлов");
                out.add(new RemoteEntry(name,join(path,name),a.isDir(),a.getSize(),a.getMTime()));}
            return out;
        }
        private boolean exists(String path)throws Exception{try{c.sftp.lstat(literal(path));return true;}catch(com.jcraft.jsch.SftpException e){if(e.id==ChannelSftp.SSH_FX_NO_SUCH_FILE)return false;throw e;}}
        String unique(String parent,String name)throws Exception{
            TransferPaths.name(name);String path=join(parent,name);if(!exists(path))return path;
            for(int i=1;i<10000;i++){String next=join(parent,TransferPaths.duplicate(name,i));if(!exists(next))return next;}
            throw new java.io.IOException("Слишком много одинаковых имён");
        }
        String folder(String parent,String name)throws Exception{String path=unique(parent,name);c.sftp.mkdir(path);return path;}
        void read(String path,OutputStream out,ProgressCallback progress)throws Exception{
            if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Передача отменена");
            SftpATTRS a=c.sftp.lstat(literal(path));if(a.isLink() || !a.isReg())throw new java.io.IOException("Недопустимый файл");
            final long[] copied={0};final boolean[] keepGoing={true};
            c.sftp.get(literal(path),out,monitor((n,total)->{copied[0]=n;keepGoing[0]=keepGoing[0] && (progress==null || progress.onProgress(n,total));return keepGoing[0];},a.getSize()));
            if(!keepGoing[0] || Thread.currentThread().isInterrupted() || copied[0]!=a.getSize())throw new java.io.InterruptedIOException("Передача не завершена");
            SftpATTRS after=c.sftp.lstat(literal(path));
            if(after.isLink() || !after.isReg() || after.getSize()!=a.getSize() || after.getMTime()!=a.getMTime())throw new java.io.IOException("Файл на сервере изменился во время скачивания. Повтори передачу.");
            out.flush();
        }
        String write(String parent,String name,InputStream input,long size,ProgressCallback progress)throws Exception{
            TransferPaths.name(name);String temp=join(parent,".localls-upload-"+java.util.UUID.randomUUID()+".part");
            boolean moved=false;final long[] copied={0};final boolean[] keepGoing={true};
            try{c.sftp.put(input,literal(temp),monitor((n,total)->{copied[0]=n;keepGoing[0]=keepGoing[0] && (progress==null || progress.onProgress(n,total));return keepGoing[0];},size),ChannelSftp.OVERWRITE);
                if(!keepGoing[0] || Thread.currentThread().isInterrupted() || (size>0 && copied[0]!=size))throw new java.io.InterruptedIOException();
                // Resolve the name after streaming; another upload may have finished meanwhile.
                for(int attempt=0;attempt<16;attempt++){
                    if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
                    String target=unique(parent,name);
                    try{c.sftp.rename(literal(temp),literal(target));moved=true;return target;}
                    catch(com.jcraft.jsch.SftpException error){if(!exists(target))throw error;}
                }
                throw new java.io.IOException("Имя одновременно занимают другие передачи. Повтори копирование.");
            }finally{if(!moved)try{c.sftp.rm(literal(temp));}catch(Exception ignored){}}
        }
        @Override public void close(){c.close();}
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
