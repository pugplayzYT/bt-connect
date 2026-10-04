package dev.btconnect;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.Context;
import java.io.*;
import java.util.concurrent.*;

@SuppressLint("MissingPermission") // Activity checks runtime permissions before constructing a connection.
final class BluetoothConnection implements AutoCloseable {
    interface Listener { void connected(); void frame(Protocol.Frame frame); void disconnected(String reason); }
    private final BluetoothSocket socket;
    private final AudioPlayer player;
    private final Listener listener;
    private final ThreadPoolExecutor sends=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32));
    private volatile boolean closed;
    private volatile OutputStream output;
    BluetoothConnection(Context context, BluetoothDevice device, Listener listener) throws IOException {
        socket=device.createRfcommSocketToServiceRecord(Protocol.SERVICE_ID);
        player=new AudioPlayer(context); this.listener=listener;
    }
    void connect(String name) {
        new Thread(() -> {
            String reason="Disconnected";
            try {
                socket.connect();
                if(closed) return;
                output=socket.getOutputStream();
                Protocol.write(output,Protocol.text(Protocol.HELLO,name));
                listener.connected();
                SessionState state=new SessionState();
                Protocol.Frame frame;
                while(!closed && (frame=Protocol.read(socket.getInputStream()))!=null) {
                    state.apply(frame);
                    if(frame.type==Protocol.AUDIO_START) player.start();
                    else if(frame.type==Protocol.AUDIO_STOP) player.close();
                    else if(frame.type==Protocol.AUDIO) player.write(frame.data);
                    if(frame.type!=Protocol.AUDIO) listener.frame(frame);
                }
            } catch(IOException | RuntimeException e) { reason="Disconnected: "+e.getMessage(); }
            finally { close(); listener.disconnected(reason); }
        },"bt-connect-reader").start();
    }
    void send(String text) throws IOException {
        Protocol.Frame frame=Protocol.text(Protocol.CHAT,text);
        if(output==null || closed) throw new IOException("Not connected");
        try { sends.execute(() -> {
            try { Protocol.write(output,frame); }
            catch(IOException e) { close(); }
        }); } catch(RejectedExecutionException e) { throw new IOException("Too many pending messages",e); }
    }
    @Override public void close() {
        closed=true; sends.shutdownNow();
        try { socket.close(); } catch(IOException ignored) { }
        player.close();
    }
}
