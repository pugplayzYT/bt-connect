package dev.btconnect;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;

public class ProtocolTest {
    private static byte[] hex(String text) {
        byte[] b=new byte[text.length()/2]; for(int i=0;i<b.length;i++) b[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16); return b;
    }
    @Test public void sharedFixturesRoundTripWithFragmentedReads() throws Exception {
        for(String line:Files.readAllLines(Paths.get("../../protocol/fixtures.txt"))) {
            if(line.startsWith("#")) continue;
            String[] parts=line.split("\\|"); byte[] wire=hex(parts[2]);
            InputStream stream=new ByteArrayInputStream(wire) {
                @Override public synchronized int read(byte[] b,int off,int len) { return super.read(b,off,Math.min(1,len)); }
            };
            Protocol.Frame frame=Protocol.read(stream); assertNotNull(frame); assertEquals(Integer.parseInt(parts[1]),frame.type);
            ByteArrayOutputStream output=new ByteArrayOutputStream(); Protocol.write(output,frame);
            assertArrayEquals(wire,output.toByteArray()); assertNull(Protocol.read(stream));
        }
    }
    @Test public void concatenatedFramesRemainSeparate() throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        Protocol.write(out,Protocol.text(Protocol.CHAT,"hello 👋")); Protocol.write(out,new Protocol.Frame(Protocol.AUDIO_STOP,new byte[0]));
        InputStream in=new ByteArrayInputStream(out.toByteArray());
        assertEquals("hello 👋",Protocol.text(Protocol.read(in))); assertEquals(Protocol.AUDIO_STOP,Protocol.read(in).type); assertNull(Protocol.read(in));
    }
    @Test public void rejectsMalformedAndTruncatedFrames() {
        for(String wire:new String[]{"42","42540102050000004869","4254020600000000","425401ff00000000","42540102ffffffff","4254010201400000","4254010201000000ff","425401050100000000","425401040800000044ac000001001000","4254010304000000ffffffff"})
            assertThrows(IOException.class,() -> Protocol.read(new ByteArrayInputStream(hex(wire))));
    }
    @Test public void enforcesUtf8ByteLimit() {
        assertThrows(IOException.class,() -> Protocol.text(Protocol.CHAT,"é".repeat(1025)));
    }
}
