package dev.btconnect;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.UUID;

public class DiscoverySessionTest {
    @Test public void compatibleCachedServiceAppearsOnlyOnce() {
        DiscoverySession s=new DiscoverySession(); s.begin(); s.candidate("computer");
        assertTrue(s.services("computer",new UUID[]{Protocol.SERVICE_ID}));
        assertFalse(s.services("computer",new UUID[]{Protocol.SERVICE_ID})); assertEquals(1,s.count());
    }
    @Test public void laterCompatibleResponseIsAcceptedAfterEmptyResponse() {
        DiscoverySession s=new DiscoverySession(); s.begin(); s.candidate("computer");
        assertFalse(s.services("computer",null));
        assertFalse(s.services("computer",new UUID[0]));
        s.candidate("other"); // Probing has moved on; the old computer's response still counts.
        assertTrue(s.services("computer",new UUID[]{Protocol.SERVICE_ID}));
    }
    @Test public void unrelatedDevicesAndServicesAreNotListed() {
        DiscoverySession s=new DiscoverySession(); s.begin(); s.candidate("computer");
        assertFalse(s.services("unknown",new UUID[]{Protocol.SERVICE_ID}));
        assertFalse(s.services("computer",new UUID[]{UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")}));
        assertEquals(0,s.count());
    }
    @Test public void StopAndNewScanRejectStaleDeviceResults() {
        DiscoverySession s=new DiscoverySession(); s.begin(); s.candidate("old"); s.stop();
        assertFalse(s.services("old",new UUID[]{Protocol.SERVICE_ID}));
        s.begin(); s.candidate("new"); assertFalse(s.services("old",new UUID[]{Protocol.SERVICE_ID}));
        assertTrue(s.services("new",new UUID[]{Protocol.SERVICE_ID}));
    }
}
