package com.bitpoint.homeservercontrol;

import android.content.*;
import android.os.*;

final class DeviceTelemetry {
    static ServerTelemetry read(Context context){
        int battery=-1,charging=-1;long free=-1,total=-1;
        try{Intent state=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(state!=null){int status=state.getIntExtra(BatteryManager.EXTRA_STATUS,-1);if(status==BatteryManager.BATTERY_STATUS_CHARGING||status==BatteryManager.BATTERY_STATUS_FULL)charging=1;else if(status==BatteryManager.BATTERY_STATUS_DISCHARGING||status==BatteryManager.BATTERY_STATUS_NOT_CHARGING)charging=0;int level=state.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=state.getIntExtra(BatteryManager.EXTRA_SCALE,-1);if(level>=0&&scale>0&&level<=scale)battery=(int)((long)level*100/scale);}
        }catch(RuntimeException ignored){}
        // This is the actual SFTP destination volume, not an unrelated SD card.
        try{StatFs fs=new StatFs(EmbeddedServerService.share(context).getAbsolutePath());free=fs.getAvailableBytes();total=fs.getTotalBytes();}catch(RuntimeException ignored){}
        return new ServerTelemetry(battery,free,total,SystemClock.elapsedRealtime()/1000,charging,DeviceLabels.local(context));
    }
}
