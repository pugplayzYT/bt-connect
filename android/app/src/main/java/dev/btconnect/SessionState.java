package dev.btconnect;

import java.io.IOException;

/** Testable state machine; chat does not interrupt an audio broadcast. */
public final class SessionState {
    public boolean broadcasting;
    public int count;
    public void apply(Protocol.Frame frame) throws IOException {
        Protocol.validate(frame);
        switch(frame.type) {
            case Protocol.COUNT: count=Protocol.count(frame); break;
            case Protocol.AUDIO_START: broadcasting=true; break;
            case Protocol.AUDIO_STOP: broadcasting=false; break;
            case Protocol.CHAT: break;
            case Protocol.AUDIO: if (!broadcasting) throw new IOException("Audio before broadcast started"); break;
            default: throw new IOException("Unexpected server frame");
        }
    }
    public void disconnect() { broadcasting=false; count=0; }
}
