package com.bitpoint.homeservercontrol;

import java.io.IOException;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.sftp.server.SftpSubsystem;
import org.apache.sshd.sftp.server.SftpSubsystemConfigurator;

/** Validate framing before retaining client bytes or scheduling filesystem work. */
final class BoundedSftpSubsystem extends SftpSubsystem {
    // Includes the normal 256 KiB WRITE payload and its protocol overhead.
    static final int MAX_FRAME=256*1024+1024, MAX_PENDING=128;
    BoundedSftpSubsystem(ChannelSession channel,SftpSubsystemConfigurator config){super(channel,config);}

    @Override public synchronized int data(ChannelSession channel,byte[] bytes,int offset,int length)throws IOException{
        while(length>0){
            int have=buffer.available();
            if(have<4){
                int count=Math.min(4-have,length);
                buffer.putRawBytes(bytes,offset,count);offset+=count;length-=count;
                have+=count;if(have<4)continue;
            }
            int position=buffer.rpos();byte[] header=buffer.array();
            long frame=((header[position]&255L)<<24)|((header[position+1]&255L)<<16)
                    |((header[position+2]&255L)<<8)|(header[position+3]&255L);
            if(frame<5||frame>MAX_FRAME){
                channel.close(true);throw new IOException("Invalid SFTP frame length");
            }
            int count=Math.min((int)frame+4-have,length);
            buffer.putRawBytes(bytes,offset,count);offset+=count;length-=count;
            if(buffer.available()==frame+4){
                if(requests.size()>=MAX_PENDING){
                    channel.close(true);throw new IOException("Too many pending SFTP requests");
                }
                ByteArrayBuffer request=new ByteArrayBuffer((int)frame+4,false);
                request.putRawBytes(buffer.array(),buffer.rpos(),buffer.available());
                requests.add(request);buffer.clear();
            }
        }
        return 0; // The worker releases SSH window bytes after processing each frame.
    }
}
