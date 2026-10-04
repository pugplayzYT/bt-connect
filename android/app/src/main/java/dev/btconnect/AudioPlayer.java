package dev.btconnect;

import android.content.Context;
import android.media.*;
import java.io.IOException;

final class AudioPlayer implements AutoCloseable {
    private final AudioManager manager;
    private final AudioFocusRequest focus;
    private AudioTrack track;
    AudioPlayer(Context context) {
        manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener(change -> {
                synchronized(this) { if(track!=null) track.setVolume(change==AudioManager.AUDIOFOCUS_GAIN ? 1f : 0f); }
            }).build();
    }
    synchronized void start() throws IOException {
        close();
        if (manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            throw new IOException("Audio focus unavailable. Close the other audio app and reconnect.");
        int minimum=AudioTrack.getMinBufferSize(Protocol.SAMPLE_RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
        track=new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(new AudioFormat.Builder().setSampleRate(Protocol.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(minimum,6400)).build();
        if (track.getState()!=AudioTrack.STATE_INITIALIZED) { close(); throw new IOException("Could not initialize audio output"); }
        track.play();
    }
    synchronized void write(byte[] pcm) throws IOException {
        if(track==null) throw new IOException("Audio output is stopped");
        int offset=0;
        while(offset<pcm.length) {
            int written=track.write(pcm,offset,pcm.length-offset,AudioTrack.WRITE_BLOCKING);
            if(written<=0) throw new IOException("Audio playback failed");
            offset+=written;
        }
    }
    @Override public synchronized void close() {
        if(track!=null) {
            if(track.getState()==AudioTrack.STATE_INITIALIZED) { track.pause(); track.flush(); }
            track.release(); track=null;
        }
        manager.abandonAudioFocusRequest(focus);
    }
}
