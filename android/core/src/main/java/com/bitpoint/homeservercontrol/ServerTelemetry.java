package com.bitpoint.homeservercontrol;

/** Small, versioned metrics and a bounded device-kind enum. No personal identifiers or credentials. */
final class ServerTelemetry {
    static final String SUBSYSTEM="localls-status-v1";
    final int battery;
    final int charging;
    final long free,total,uptime;
    final DeviceKind deviceKind;
    ServerTelemetry(int battery,long free,long total,long uptime){this(battery,free,total,uptime,-1);}
    ServerTelemetry(int battery,long free,long total,long uptime,int charging){
        this(battery,free,total,uptime,charging,DeviceKind.UNKNOWN);
    }
    ServerTelemetry(int battery,long free,long total,long uptime,int charging,DeviceKind deviceKind){
        this.deviceKind=java.util.Objects.requireNonNull(deviceKind);
        if(charging < -1 || charging > 1)throw new IllegalArgumentException("Invalid charging state");this.charging=charging;
        if(battery < -1 || battery > 100 || free < -1 || total < -1 || uptime < -1 || (free>=0 && total>=0 && free>total))throw new IllegalArgumentException("Invalid server metrics");
        this.battery=battery;this.free=free;this.total=total;this.uptime=uptime;
    }
    String encode(){return SUBSYSTEM+"\n"+battery+"\n"+free+"\n"+total+"\n"+uptime+"\n";}
    String encodeV2(){return encode().replace(SUBSYSTEM,"localls-status-v2")+charging+"\n";}
    String encodeV3(){return encodeV2().replace("localls-status-v2","localls-status-v3")+deviceKind.wire+"\n";}
    static ServerTelemetry decode(String wire){
        if(wire==null || wire.length()>256)throw new IllegalArgumentException("Invalid server metrics");
        if(wire.startsWith("localls-status-v3\n")){
            String[] p=wire.split("\n",-1);if(p.length!=8||!p[7].isEmpty())throw new IllegalArgumentException("Invalid server metrics");
            ServerTelemetry v=decode("localls-status-v2\n"+p[1]+"\n"+p[2]+"\n"+p[3]+"\n"+p[4]+"\n"+p[5]+"\n");
            return new ServerTelemetry(v.battery,v.free,v.total,v.uptime,v.charging,DeviceKind.decode(p[6]));
        }
        if(wire!=null&&wire.startsWith("localls-status-v2\n")){
            String[] p=wire.split("\n",-1);if(p.length!=7||!p[6].isEmpty()||!p[5].matches("-1|0|1"))throw new IllegalArgumentException("Invalid server metrics");
            ServerTelemetry v=decode(SUBSYSTEM+"\n"+p[1]+"\n"+p[2]+"\n"+p[3]+"\n"+p[4]+"\n");return new ServerTelemetry(v.battery,v.free,v.total,v.uptime,Integer.parseInt(p[5]));
        }
        if(wire==null || wire.length()>256)throw new IllegalArgumentException("Invalid server metrics");
        String[] fields=wire.split("\n",-1);
        if(fields.length!=6 || !SUBSYSTEM.equals(fields[0]) || !fields[5].isEmpty())throw new IllegalArgumentException("Invalid server metrics");
        for(int i=1;i<=4;i++)if(!fields[i].matches("-1|0|[1-9][0-9]{0,18}"))throw new IllegalArgumentException("Invalid server metrics");
        return new ServerTelemetry(Integer.parseInt(fields[1]),Long.parseLong(fields[2]),Long.parseLong(fields[3]),Long.parseLong(fields[4]));
    }
    static String bytes(long n){if(n<0)return "—";if(n<1024)return n+" Б";if(n<1048576)return String.format(java.util.Locale.ROOT,"%.1f КБ",n/1024d);if(n<1073741824)return String.format(java.util.Locale.ROOT,"%.1f МБ",n/1048576d);return String.format(java.util.Locale.ROOT,"%.1f ГБ",n/1073741824d);}
    void apply(ServerStats stats){stats.telemetryAvailable=true;stats.freeBytes=free;stats.totalBytes=total;stats.uptimeSeconds=uptime;stats.charging=charging==1?"Заряжается":charging==0?"От батареи":"Статус зарядки недоступен";stats.battery=battery<0?"—":battery+"%";stats.freeSpace=bytes(free);stats.disk="всего "+bytes(total)+" • свободно "+bytes(free);stats.uptime=uptime<0?"—":uptime>=86400?(uptime/86400)+" д "+(uptime%86400/3600)+" ч":(uptime/3600)+" ч "+(uptime%3600/60)+" мин";}
}
