package com.bitpoint.homeservercontrol;
import org.junit.Test;
import static org.junit.Assert.*;
public class ServerTelemetryTest {
 @Test public void exactRoundTripIncludesZeroAndUnavailableValues(){
  for(var data:new ServerTelemetry[]{new ServerTelemetry(0,0,4000000000L,0),new ServerTelemetry(100,2000000000L,4000000000L,3660),new ServerTelemetry(-1,-1,-1,-1)}){
   var got=ServerTelemetry.decode(data.encode());assertEquals(data.battery,got.battery);assertEquals(data.free,got.free);assertEquals(data.total,got.total);assertEquals(data.uptime,got.uptime);
  }
  ServerStats stats=new ServerStats();new ServerTelemetry(73,2147483648L,4294967296L,3660).apply(stats);assertEquals("73%",stats.battery);assertEquals("2.0 ГБ",stats.freeSpace);assertEquals("1 ч 1 мин",stats.uptime);
 }
 @Test public void malformedOversizedOrImpossibleMetricsCannotBeDisplayed(){
  for(String wire:new String[]{"", "x".repeat(257),"localls-status-v1\n101\n0\n10\n0\n","localls-status-v1\n5\n11\n10\n0\n","localls-status-v1\n5\n-2\n10\n0\n","localls-status-v1\n5\n0\n10\n0\nsecret","localls-status-v1\n5\n9223372036854775808\n10\n0\n","localls-status-v1\n5\n0\n10\n0"})try{ServerTelemetry.decode(wire);fail(wire);}catch(IllegalArgumentException expected){}
 }
 @Test public void chargingExtensionKeepsOldWireCompatible(){
  var value=new ServerTelemetry(35,137L*1073741824,256L*1073741824,1080000,1);assertEquals(-1,ServerTelemetry.decode(value.encode()).charging);assertEquals(1,ServerTelemetry.decode(value.encodeV2()).charging);ServerStats stats=new ServerStats();ServerTelemetry.decode(value.encodeV2()).apply(stats);assertEquals("Заряжается",stats.charging);assertEquals(137L*1073741824,stats.freeBytes);assertEquals(256L*1073741824,stats.totalBytes);
 }
 @Test public void unknownOrForgedChargingValuesAreRejected(){for(String ending:new String[]{"2\n","-2\n","1\nsecret"})try{ServerTelemetry.decode("localls-status-v2\n35\n10\n20\n30\n"+ending);fail();}catch(IllegalArgumentException expected){}}
}
