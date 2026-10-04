package dev.btconnect;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.UUID;

/** Versioned, bounded RFCOMM frames. Identical wire format to BtConnect.Core. */
public final class Protocol {
    public static final UUID SERVICE_ID = UUID.fromString("84c39d30-2f4b-4d7f-9d81-40c18b587830");
    public static final int HELLO=1, CHAT=2, COUNT=3, AUDIO_START=4, AUDIO=5, AUDIO_STOP=6;
    public static final int SAMPLE_RATE=16000, MAX_PAYLOAD=16384;
    public static final class Frame {
        public final int type;
        public final byte[] data;
        public Frame(int type, byte[] data) { this.type=type; this.data=data; }
    }
    private Protocol() {}
    public static Frame text(int type, String text) throws IOException {
        Frame frame = new Frame(type, text.getBytes(StandardCharsets.UTF_8)); validate(frame); return frame;
    }
    public static String text(Frame frame) throws IOException {
        validate(frame);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(frame.data)).toString();
    }
    public static int count(Frame frame) throws IOException {
        validate(frame);
        if (frame.type!=COUNT) throw new IOException("Not a count frame");
        return ByteBuffer.wrap(frame.data).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }
    public static void validate(Frame frame) throws IOException {
        int size=frame.data.length;
        ByteBuffer b=ByteBuffer.wrap(frame.data).order(ByteOrder.LITTLE_ENDIAN);
        boolean valid;
        switch (frame.type) {
            case HELLO: valid=size>0 && size<=128; break;
            case CHAT: valid=size>0 && size<=2048; break;
            case COUNT: valid=size==4 && b.getInt()>=0; break;
            case AUDIO_START: valid=size==8 && b.getInt()==SAMPLE_RATE && b.getShort()==1 && b.getShort()==16; break;
            case AUDIO: valid=size>0 && size<=4096 && size%2==0; break;
            case AUDIO_STOP: valid=size==0; break;
            default: valid=false;
        }
        if (!valid) throw new IOException("Invalid Bluetooth frame");
        if (frame.type==HELLO || frame.type==CHAT)
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(frame.data));
    }
    public static Frame read(InputStream stream) throws IOException {
        int first=stream.read(); if (first==-1) return null;
        byte[] header=new byte[8]; header[0]=(byte)first; readFully(stream,header,1,7);
        if (header[0]!=0x42 || header[1]!=0x54 || header[2]!=1) throw new IOException("Unsupported protocol");
        int size=ByteBuffer.wrap(header,4,4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (size<0 || size>MAX_PAYLOAD) throw new IOException("Frame too large");
        byte[] data=new byte[size]; readFully(stream,data,0,size);
        Frame frame=new Frame(header[3]&0xff,data); validate(frame); return frame;
    }
    public static void write(OutputStream stream, Frame frame) throws IOException {
        validate(frame);
        byte[] header=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .put((byte)0x42).put((byte)0x54).put((byte)1).put((byte)frame.type).putInt(frame.data.length).array();
        stream.write(header); stream.write(frame.data); stream.flush();
    }
    private static void readFully(InputStream stream, byte[] b, int offset, int count) throws IOException {
        while(count>0) {
            int n=stream.read(b,offset,count);
            if (n<0) throw new EOFException("Truncated Bluetooth frame");
            if (n==0) { int value=stream.read(); if(value<0) throw new EOFException(); b[offset]=(byte)value; n=1; }
            offset+=n; count-=n;
        }
    }
}
