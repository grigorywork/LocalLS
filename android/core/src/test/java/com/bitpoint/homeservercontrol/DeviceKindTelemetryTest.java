package com.bitpoint.homeservercontrol;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeviceKindTelemetryTest {
    @Test public void explicitKindsRoundTripWhileOldFormatsStayUnchanged(){
        for(DeviceKind kind:DeviceKind.values()){
            var source=new ServerTelemetry(47,10,20,30,0,kind);
            assertEquals(kind,ServerTelemetry.decode(source.encodeV3()).deviceKind);
            assertEquals(DeviceKind.UNKNOWN,ServerTelemetry.decode(source.encodeV2()).deviceKind);
            assertEquals(DeviceKind.UNKNOWN,ServerTelemetry.decode(source.encode()).deviceKind);
            assertEquals(0,ServerTelemetry.decode(source.encodeV3()).charging);
        }
    }
    @Test public void unsupportedOrInjectedKindCannotBecomeADeviceLabel(){
        for(String kind:new String[]{"Phone","laptop","pc\nsecret","<script>","", "x".repeat(257)}){
            try{ServerTelemetry.decode("localls-status-v3\n47\n10\n20\n30\n0\n"+kind+"\n");fail(kind);}
            catch(IllegalArgumentException expected){}
        }
        assertEquals("Телефон",DeviceKind.PHONE.label);assertEquals("Планшет",DeviceKind.TABLET.label);
        assertEquals("ПК",DeviceKind.PC.label);assertEquals("Сервер",DeviceKind.UNKNOWN.label);
    }
}
