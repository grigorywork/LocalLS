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

    /** A pinned, public-key-only SSH attempt. No file channel or password is opened. */
    static boolean pairingAttempt(String host,int port,String user,String nonce,SshCredentials key,String pin)throws Exception{
        if(!ConnectionEndpoint.validHost(host)||!ConnectionEndpoint.validUser(user)||port<1||port>65535||nonce==null||!nonce.matches("[a-f0-9-]{36}")||pin==null||!pin.matches("SHA256:[A-Za-z0-9+/]{43}")||!key.keyMode())throw new IllegalArgumentException("Неверный запрос QR");
        return pinnedLogin(host,port,user+"#"+nonce,key,pin);
    }
    static boolean loginAttempt(String host,int port,String user,SshCredentials key,String pin)throws Exception{
        if(!ConnectionEndpoint.validHost(host)||!ConnectionEndpoint.validUser(user)||port<1||port>65535||pin==null||!pin.matches("SHA256:[A-Za-z0-9+/]{43}")||!key.keyMode())throw new IllegalArgumentException("Неверные параметры входа");
        return pinnedLogin(host,port,user,key,pin);
    }
    private static boolean pinnedLogin(String host,int port,String login,SshCredentials key,String pin)throws Exception{
        VerifiedCopy.check();JSch jsch=new JSch();PinnedHostKeyRepository trust=new PinnedHostKeyRepository(jsch,pin);jsch.setHostKeyRepository(trust);
        Session session=jsch.getSession(login,host,port);
        try{
            key.configure(jsch,session);session.setTimeout(3500);
            try{session.connect(3500);}catch(com.jcraft.jsch.JSchException error){
                String actual=trust.getLastFingerprint();if(!actual.isEmpty()&&!pin.equals(actual))throw new SecurityException("Ключ сервера изменился",error);
                if(pin.equals(actual)&&error.getMessage()!=null&&error.getMessage().startsWith("Auth fail"))return false;
                throw error;
            }
            VerifiedCopy.check();if(!pin.equals(trust.getLastFingerprint()))throw new SecurityException("Ключ сервера изменился");return session.isConnected();
        }finally{session.disconnect();try{jsch.removeAllIdentity();}catch(com.jcraft.jsch.JSchException ignored){}}
    }

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
        final DeviceKind deviceKind;
        DirectoryListing(String path, List<RemoteEntry> entries) {
            this(path,entries,DeviceKind.UNKNOWN);
        }
        DirectoryListing(String path,List<RemoteEntry> entries,DeviceKind deviceKind){
            this.path = path;
            this.entries = entries;
            this.deviceKind=deviceKind;
        }
    }

    static DirectoryListing browse(String host, int port, String user, SshCredentials credential,
                                   String trustedFingerprint, String requestedPath) throws Exception {
        return browse(host,port,user,credential,trustedFingerprint,requestedPath,false);
    }
    static DirectoryListing browse(String host,int port,String user,SshCredentials credential,
                                   String trustedFingerprint,String requestedPath,boolean identify) throws Exception {
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
            return new DirectoryListing(actualPath,out,identify?SshClient.readDeviceKind(connection.session):DeviceKind.UNKNOWN);
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
        private java.util.function.LongConsumer payload;
        TreeSession(String host,int port,String user,SshCredentials key,String pin)throws Exception{c=connect(host,port,user,key,pin);}
        void onPayload(java.util.function.LongConsumer listener){payload=java.util.Objects.requireNonNull(listener);}
        private InputStream metered(InputStream input){
            if(payload==null)return input;final var listener=payload;
            return new java.io.FilterInputStream(input){
                @Override public int read()throws java.io.IOException{int value=in.read();if(value>=0)listener.accept(1);return value;}
                @Override public int read(byte[] b,int off,int len)throws java.io.IOException{int n=in.read(b,off,len);if(n>0)listener.accept(n);return n;}
            };
        }
        private OutputStream metered(OutputStream output){
            if(payload==null)return output;final var listener=payload;
            return new java.io.FilterOutputStream(output){
                @Override public void write(int value)throws java.io.IOException{out.write(value);listener.accept(1);}
                @Override public void write(byte[] b,int off,int len)throws java.io.IOException{out.write(b,off,len);if(len>0)listener.accept(len);}
            };
        }
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
        RemoteEntry entry(String path)throws Exception{
            try{SftpATTRS a=c.sftp.lstat(literal(path));if(a.isLink()||(!a.isDir()&&!a.isReg()))throw new java.io.IOException("Недопустимый файл");
                return new RemoteEntry(new java.io.File(path).getName(),path,a.isDir(),a.getSize(),a.getMTime());
            }catch(com.jcraft.jsch.SftpException e){if(e.id==ChannelSftp.SSH_FX_NO_SUCH_FILE)return null;throw e;}
        }
        /** -1 means the server does not implement the capacity extension. Never guess zero. */
        long available(String path)throws Exception{
            try{com.jcraft.jsch.SftpStatVFS info=c.sftp.statVFS(literal(path));try{return Math.multiplyExact(info.getFragmentSize(),info.getAvailBlocks());}catch(ArithmeticException e){return Long.MAX_VALUE;}}
            catch(com.jcraft.jsch.SftpException e){if(e.id==ChannelSftp.SSH_FX_OP_UNSUPPORTED)return -1;throw e;}
        }
        String ensureFolder(String path)throws Exception{RemoteEntry e=entry(path);if(e==null)c.sftp.mkdir(literal(path));else if(!e.directory)throw new java.io.IOException("На месте папки уже есть файл");return path;}
        String checksum(String path)throws Exception{try(InputStream in=metered(c.sftp.get(literal(path)))){return VerifiedCopy.sha256(in);}}
        void recoverPublished(String target,String temporaryName,String expectedHash)throws Exception{
            TransferPaths.name(temporaryName);if(!temporaryName.startsWith(".localls-upload-")||!temporaryName.endsWith(".part")||!expectedHash.equals(checksum(target)))throw new java.io.IOException("Публикация не подтверждена");
            String temporary=join(parent(target),temporaryName);for(String path:new String[]{temporary,temporary+".previous"}){RemoteEntry e=entry(path);if(e!=null){if(e.directory)throw new java.io.IOException("Временное имя занято папкой");c.sftp.rm(literal(path));}}
        }
        String identity(String path)throws Exception{RemoteEntry e=entry(path);if(e==null||e.directory)throw new java.io.IOException("Файл недоступен");return e.size+":"+e.mtime;}
        /** Verify every retained byte before appending; keep private staging on a network failure. */
        void readResumable(String path,java.io.File partial,ProgressCallback progress)throws Exception{
            String before=identity(path);RemoteEntry e=entry(path);long offset=partial.length();
            if(offset>e.size)throw new java.io.IOException("Частичный файл длиннее исходника");
            try(InputStream source=metered(c.sftp.get(literal(path)))){
                if(offset>0)try(InputStream saved=new java.io.FileInputStream(partial)){VerifiedCopy.prefix(source,saved,offset,(n,total)->{if(progress!=null&&!progress.onProgress(0,e.size))throw new ResumeCancelled();});}
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(partial,true)){
                    VerifiedCopy.copy(source,out,offset,e.size,(n,total)->{if(progress!=null&&!progress.onProgress(n,total))throw new ResumeCancelled();});out.getFD().sync();
                }
            }catch(ResumeCancelled e1){throw new java.io.InterruptedIOException("Передача отменена");}
            if(!before.equals(identity(path)))throw new java.io.IOException("Файл на сервере изменился во время скачивания");
        }
        /** A caller-owned stable temporary name makes a retry resumable across processes. */
        interface BeforePublish{void ready(String target,String sha256)throws Exception;}
        String writeResumable(String parent,String name,InputStream source,long size,ProgressCallback progress,
                String tempName,String target,String policy,boolean verify,BeforePublish checkpoint)throws Exception{
            TransferPaths.name(name);TransferPaths.name(tempName);
            if(!tempName.startsWith(".localls-upload-")||!tempName.endsWith(".part"))throw new java.io.IOException("Неверное имя дозагрузки");
            String temp=join(parent,tempName);RemoteEntry old=entry(temp);if(old!=null&&old.directory)throw new java.io.IOException("Недопустимый временный файл");
            long offset=old==null?0:old.size;if(size>=0&&offset>size)throw new java.io.IOException("Частичный файл длиннее исходника");
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            InputStream input=new java.security.DigestInputStream(source,digest);
            if(offset>0)try(InputStream saved=metered(c.sftp.get(literal(temp)))){VerifiedCopy.prefix(input,saved,offset,(n,total)->{if(progress!=null&&!progress.onProgress(0,size))throw new ResumeCancelled();});}
                catch(ResumeCancelled cancelled){throw new java.io.InterruptedIOException("Передача отменена");}
            final long base=offset;final boolean[] keep={true};final long[] count={offset};
            try(OutputStream out=metered(c.sftp.put(literal(temp),monitor((n,total)->{
                count[0]=base+n;keep[0]=keep[0]&&(progress==null||progress.onProgress(base+n,size));return keep[0];
            },size<0?0:Math.max(0,size-offset)),offset>0?ChannelSftp.RESUME:ChannelSftp.OVERWRITE))){
                byte[] block=new byte[32768];int n;while((n=input.read(block))!=-1){VerifiedCopy.check();if(!keep[0])throw new java.io.InterruptedIOException();out.write(block,0,n);}
            }
            VerifiedCopy.check();if(!keep[0])throw new java.io.InterruptedIOException();
            RemoteEntry finished=entry(temp);if(finished==null||(size>=0&&finished.size!=size)||finished.size!=count[0])throw new java.io.IOException("Исходный файл изменился");
            String hash=VerifiedCopy.hex(digest.digest());
            if(verify&&!hash.equals(checksum(temp)))throw new java.io.IOException("SHA-256 копии не совпадает");
            if(target==null||target.isEmpty())target="keep".equals(policy)?unique(parent,name):join(parent,name);
            if(checkpoint!=null)checkpoint.ready(target,hash);
            publish(temp,target,policy);return target;
        }
        /** Stash the old file until the new complete copy is published. Never truncate it. */
        private void publish(String temp,String target,String policy)throws Exception{
            RemoteEntry prior=entry(target);
            if(prior!=null){
                if(!"replace".equals(policy)||prior.directory)throw new java.io.IOException("Имя назначения уже занято");
                String backup=temp+".previous";
                if(entry(backup)!=null)throw new java.io.IOException("Сохранена прежняя копия после обрыва. Проверь "+backup);
                c.sftp.rename(literal(target),literal(backup));
                try{c.sftp.rename(literal(temp),literal(target));}
                catch(Exception error){try{c.sftp.rename(literal(backup),literal(target));}catch(Exception restore){error.addSuppressed(restore);}throw error;}
                c.sftp.rm(literal(backup));
            }else{
                String backup=temp+".previous";RemoteEntry saved=entry(backup);
                if(saved!=null&&(!"replace".equals(policy)||saved.directory))throw new java.io.IOException("Не подтверждена принадлежность резервной копии");
                try{c.sftp.rename(literal(temp),literal(target));}
                catch(Exception error){if(saved!=null)try{if(entry(target)==null)c.sftp.rename(literal(backup),literal(target));}catch(Exception restore){error.addSuppressed(restore);}throw error;}
                if(saved!=null)c.sftp.rm(literal(backup));
            }
        }
        private static final class ResumeCancelled extends RuntimeException{}
        void read(String path,OutputStream out,ProgressCallback progress)throws Exception{
            if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Передача отменена");
            SftpATTRS a=c.sftp.lstat(literal(path));if(a.isLink() || !a.isReg())throw new java.io.IOException("Недопустимый файл");
            final long[] copied={0};final boolean[] keepGoing={true};
            c.sftp.get(literal(path),metered(out),monitor((n,total)->{copied[0]=n;keepGoing[0]=keepGoing[0] && (progress==null || progress.onProgress(n,total));return keepGoing[0];},a.getSize()));
            if(!keepGoing[0] || Thread.currentThread().isInterrupted() || copied[0]!=a.getSize())throw new java.io.InterruptedIOException("Передача не завершена");
            SftpATTRS after=c.sftp.lstat(literal(path));
            if(after.isLink() || !after.isReg() || after.getSize()!=a.getSize() || after.getMTime()!=a.getMTime())throw new java.io.IOException("Файл на сервере изменился во время скачивания. Повтори передачу.");
            out.flush();
        }
        String write(String parent,String name,InputStream input,long size,ProgressCallback progress)throws Exception{
            TransferPaths.name(name);String temp=join(parent,".localls-upload-"+java.util.UUID.randomUUID()+".part");
            boolean moved=false;final long[] copied={0};final boolean[] keepGoing={true};
            try{
                // Closing the remote stream drains WRITE replies and closes the handle even
                // when reading the local source fails. Cleanup must use an aligned channel.
                try(OutputStream output=metered(c.sftp.put(literal(temp),monitor((n,total)->{copied[0]=n;keepGoing[0]=keepGoing[0] && (progress==null || progress.onProgress(n,total));return keepGoing[0];},size),ChannelSftp.OVERWRITE))){
                    byte[] buffer=new byte[32768];int count;
                    while((count=input.read(buffer))!=-1){
                        if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
                        output.write(buffer,0,count);
                    }
                }catch(java.io.IOException error){
                    if(!keepGoing[0] || Thread.currentThread().isInterrupted()){
                        java.io.InterruptedIOException cancelled=new java.io.InterruptedIOException("Передача отменена");
                        cancelled.initCause(error);throw cancelled;
                    }
                    if(error.getCause() instanceof com.jcraft.jsch.SftpException)throw (com.jcraft.jsch.SftpException)error.getCause();
                    throw new com.jcraft.jsch.SftpException(ChannelSftp.SSH_FX_FAILURE,"Передача не завершена",error);
                }
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
