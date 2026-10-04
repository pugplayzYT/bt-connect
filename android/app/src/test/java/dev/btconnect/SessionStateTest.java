package dev.btconnect;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.nio.*;

public class SessionStateTest {
    private Protocol.Frame start() { return new Protocol.Frame(Protocol.AUDIO_START,ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(16000).putShort((short)1).putShort((short)16).array()); }
    @Test public void chatRemainsAvailableThroughoutBroadcast() throws Exception {
        SessionState state=new SessionState(); state.apply(start());
        state.apply(Protocol.text(Protocol.CHAT,"hello")); assertTrue(state.broadcasting);
        state.apply(new Protocol.Frame(Protocol.AUDIO,new byte[]{0,0}));
        state.apply(new Protocol.Frame(Protocol.AUDIO_STOP,new byte[0])); assertFalse(state.broadcasting);
        state.apply(Protocol.text(Protocol.CHAT,"after audio")); assertFalse(state.broadcasting);
    }
    @Test public void countTracksJoinLeaveAndDisconnectResetsState() throws Exception {
        SessionState state=new SessionState(); state.apply(start());
        state.apply(new Protocol.Frame(Protocol.COUNT,new byte[]{3,0,0,0})); assertEquals(3,state.count);
        state.apply(new Protocol.Frame(Protocol.COUNT,new byte[]{2,0,0,0})); assertEquals(2,state.count);
        state.disconnect(); assertEquals(0,state.count); assertFalse(state.broadcasting);
    }
    @Test public void rejectsAudioBeforeStartAndClientGreetingFromServer() {
        SessionState state=new SessionState();
        assertThrows(IOException.class,() -> state.apply(new Protocol.Frame(Protocol.AUDIO,new byte[]{0,0})));
        assertThrows(IOException.class,() -> state.apply(Protocol.text(Protocol.HELLO,"server")));
    }
}
