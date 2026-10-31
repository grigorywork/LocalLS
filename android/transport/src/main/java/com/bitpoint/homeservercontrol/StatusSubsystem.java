package com.bitpoint.homeservercontrol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.subsystem.SubsystemFactory;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.Environment;

/** Authenticated, read-only SSH subsystem. Never executes or interprets client input. */
final class StatusSubsystem implements SubsystemFactory {
    private final Supplier<ServerTelemetry> source;private final String name;
    StatusSubsystem(Supplier<ServerTelemetry> source){this(source,ServerTelemetry.SUBSYSTEM);}
    StatusSubsystem(Supplier<ServerTelemetry> source,String name){this.source=source;this.name=name;}
    @Override public String getName(){return name;}
    @Override public Command createSubsystem(ChannelSession channel){return new Command(){
        private OutputStream out;private ExitCallback exit;
        @Override public void setInputStream(InputStream in){}
        @Override public void setOutputStream(OutputStream stream){out=stream;}
        @Override public void setErrorStream(OutputStream stream){}
        @Override public void setExitCallback(ExitCallback callback){exit=callback;}
        @Override public void start(ChannelSession session,Environment environment){
            int code=1;try{ServerTelemetry value=source.get();out.write((name.equals("localls-status-v3")?value.encodeV3():name.equals("localls-status-v2")?value.encodeV2():value.encode()).getBytes(StandardCharsets.US_ASCII));out.flush();code=0;}catch(Exception ignored){}finally{exit.onExit(code);}
        }
        @Override public void destroy(ChannelSession session){}
    };}
}
