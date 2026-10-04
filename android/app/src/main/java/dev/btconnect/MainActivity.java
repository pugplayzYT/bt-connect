package dev.btconnect;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;

@SuppressLint("MissingPermission") // Every Bluetooth entry point is guarded by ready() or permissionsGranted().
public final class MainActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter;
    private BluetoothConnection connection;
    private int generation;
    private boolean scanning, connected;
    private TextView status, count, audio, discovery;
    private LinearLayout servers, chat;
    private ScrollView chatScroll;
    private EditText input;
    private Button scan, disconnect, send;
    private final Map<String,BluetoothDevice> candidates=new LinkedHashMap<>();
    private final Set<String> listed=new HashSet<>();
    private final ArrayDeque<BluetoothDevice> probes=new ArrayDeque<>();
    private BluetoothDevice probing;
    private final Runnable probeTimeout=() -> { probing=null; probeNext(); };
    private Runnable connectTimeout;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        adapter=((BluetoothManager)getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        buildUi();
        IntentFilter filter=new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND); filter.addAction(BluetoothDevice.ACTION_UUID);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
        else registerReceiver(receiver,filter);
        if(adapter==null) { status.setText("This phone does not support Bluetooth"); scan.setEnabled(false); }
    }
    private TextView label(String text,int size) {
        TextView view=new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(Color.rgb(241,245,249));
        view.setPadding(0,dp(8),0,dp(8)); return view;
    }
    private int dp(int value) { return (int)(value*getResources().getDisplayMetrics().density); }
    private Button button(String text) { Button b=new Button(this); b.setText(text); return b; }
    private void buildUi() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(16),dp(20),dp(12)); root.setBackgroundColor(Color.rgb(16,24,39)); setContentView(root);
        if(Build.VERSION.SDK_INT>=35) root.setOnApplyWindowInsetsListener((view,insets) -> {
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            view.setPadding(dp(20)+bars.left,dp(16)+bars.top,dp(20)+bars.right,dp(12)+bars.bottom); return insets;
        });
        root.addView(label("BT Connect",30)); status=label("Find a nearby computer",16); root.addView(status);
        LinearLayout actions=new LinearLayout(this);
        scan=button("Find servers"); scan.setOnClickListener(v -> scan()); actions.addView(scan);
        disconnect=button("Disconnect"); disconnect.setEnabled(false); disconnect.setOnClickListener(v -> disconnect("Disconnected")); actions.addView(disconnect); root.addView(actions);
        discovery=label("Start the server on your computer, then scan.",14); root.addView(discovery);
        ScrollView serverScroll=new ScrollView(this); servers=new LinearLayout(this); servers.setOrientation(LinearLayout.VERTICAL); serverScroll.addView(servers);
        root.addView(serverScroll,new LinearLayout.LayoutParams(-1,dp(120)));
        count=label("0 devices connected",16); root.addView(count);
        audio=label("Chat only",20); root.addView(audio);
        root.addView(label("CHAT",14)); chatScroll=new ScrollView(this); chat=new LinearLayout(this); chat.setOrientation(LinearLayout.VERTICAL); chatScroll.addView(chat);
        root.addView(chatScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout composer=new LinearLayout(this); input=new EditText(this); input.setHint("Message"); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.LTGRAY); input.setSingleLine(); input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(400)});
        composer.addView(input,new LinearLayout.LayoutParams(0,-2,1)); send=button("Send"); send.setEnabled(false); composer.addView(send); root.addView(composer);
        send.setOnClickListener(v -> send());
        input.setOnEditorActionListener((v,id,event) -> { send(); return true; });
    }
    private boolean permissionsGranted() {
        if(Build.VERSION.SDK_INT>=31) return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    private boolean ready() {
        if(adapter==null) return false;
        if(!permissionsGranted()) {
            requestPermissions(Build.VERSION.SDK_INT>=31 ? new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1); return false;
        }
        if(!adapter.isEnabled()) { startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),2); return false; }
        return true;
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==1) { if(permissionsGranted()) scan(); else status.setText("Bluetooth permissions are needed to find and connect to servers. Enable them in app settings."); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==2 && result==RESULT_OK) scan();
    }
    private void scan() {
        if(!ready() || connection!=null) return;
        stopScan(); candidates.clear(); listed.clear(); servers.removeAllViews();
        for(BluetoothDevice device:adapter.getBondedDevices()) candidates.put(device.getAddress(),device);
        scanning=true; discovery.setText("Searching nearby computers…");
        if(!adapter.startDiscovery()) { discovery.setText("Discovery unavailable. Check Bluetooth and, on Android 8–11, enable Location."); beginProbes(); }
    }
    private void stopScan() {
        scanning=false; probing=null; probes.clear(); ui.removeCallbacks(probeTimeout);
        if(adapter!=null && permissionsGranted() && adapter.isDiscovering()) adapter.cancelDiscovery();
    }
    private final BroadcastReceiver receiver=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            if(!scanning || !permissionsGranted()) return;
            BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if(BluetoothDevice.ACTION_FOUND.equals(intent.getAction()) && device!=null) candidates.put(device.getAddress(),device);
            else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction()) && !adapter.isDiscovering() && probing==null && probes.isEmpty()) beginProbes();
            else if(BluetoothDevice.ACTION_UUID.equals(intent.getAction()) && device!=null && probing!=null && device.getAddress().equals(probing.getAddress())) {
                Parcelable[] uuids=intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                if(matches(uuids)) addServer(device);
                ui.removeCallbacks(probeTimeout); probing=null; probeNext();
            }
        }
    };
    private boolean matches(Parcelable[] uuids) {
        if(uuids!=null) for(Parcelable uuid:uuids) if(uuid instanceof ParcelUuid && Protocol.SERVICE_ID.equals(((ParcelUuid)uuid).getUuid())) return true;
        return false;
    }
    private void beginProbes() {
        if(!scanning) return;
        discovery.setText("Checking computers for BT Connect…");
        probes.addAll(candidates.values()); probeNext();
    }
    private void probeNext() {
        if(!scanning) return;
        BluetoothDevice device=probes.poll();
        if(device==null) { scanning=false; discovery.setText(listed.isEmpty() ? "No servers found. Start the computer server and pair in Bluetooth Settings, then scan again." : "Tap a computer to connect."); return; }
        // Fresh SDP lookup prevents showing a paired computer whose server has stopped.
        probing=device;
        if(device.fetchUuidsWithSdp()) ui.postDelayed(probeTimeout,6000);
        else { probing=null; ui.post(this::probeNext); }
    }
    private void addServer(BluetoothDevice device) {
        if(!listed.add(device.getAddress())) return;
        Button b=button((device.getName()==null ? "Computer" : device.getName())+"\n"+device.getAddress());
        b.setOnClickListener(v -> connect(device)); servers.addView(b);
    }
    private void connect(BluetoothDevice device) {
        if(!ready() || connection!=null) return;
        stopScan(); final int attempt=++generation;
        status.setText("Connecting to "+device.getName()+"… Confirm Bluetooth pairing if prompted.");
        scan.setEnabled(false); disconnect.setEnabled(true);
        try {
            connection=new BluetoothConnection(this,device,new BluetoothConnection.Listener() {
                private void post(Runnable action) { ui.post(() -> { if(generation==attempt) action.run(); }); }
                @Override public void connected() { post(() -> { connected=true; status.setText("Connected to "+device.getName()); send.setEnabled(true); if(connectTimeout!=null) ui.removeCallbacks(connectTimeout); }); }
                @Override public void frame(Protocol.Frame frame) { post(() -> receive(frame)); }
                @Override public void disconnected(String reason) { post(() -> disconnect(reason)); }
            });
            connectTimeout=() -> { if(generation==attempt && !connected) disconnect("Connection timed out. Pair the devices in Bluetooth Settings and try again."); };
            ui.postDelayed(connectTimeout,30000);
            // Leave ample room for the server to prepend this name to chat messages.
            String name=Build.MODEL; while(name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>100) name=name.substring(0,name.length()-1);
            connection.connect(name.isEmpty() ? "Android" : name);
        } catch(IOException | RuntimeException e) { disconnect("Could not connect: "+e.getMessage()); }
    }
    private void receive(Protocol.Frame frame) {
        try {
            if(frame.type==Protocol.COUNT) count.setText(Protocol.count(frame)+" devices connected");
            else if(frame.type==Protocol.CHAT) { chat.addView(label(Protocol.text(frame),16)); if(chat.getChildCount()>200) chat.removeViewAt(0); chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN)); }
            else if(frame.type==Protocol.AUDIO_START) audio.setText("Broadcasting audio");
            else if(frame.type==Protocol.AUDIO_STOP) audio.setText("Chat only");
        } catch(IOException e) { disconnect(e.getMessage()); }
    }
    private void send() {
        String text=input.getText().toString().trim(); if(connection==null || !connected || text.isEmpty()) return;
        try { connection.send(text); input.setText(""); }
        catch(IOException e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_SHORT).show(); }
    }
    private void disconnect(String reason) {
        ++generation; connected=false;
        if(connectTimeout!=null) ui.removeCallbacks(connectTimeout);
        BluetoothConnection old=connection; connection=null; if(old!=null) old.close();
        status.setText(reason); count.setText("0 devices connected"); audio.setText("Chat only");
        send.setEnabled(false); disconnect.setEnabled(false); scan.setEnabled(adapter!=null);
    }
    @Override protected void onStop() {
        super.onStop(); stopScan();
        // Foreground-only playback: leaving this screen always releases the socket and audio focus.
        if(connection!=null) disconnect("Disconnected while app is in background. Tap a server to reconnect.");
    }
    @Override protected void onDestroy() { unregisterReceiver(receiver); disconnect("Disconnected"); super.onDestroy(); }
}
