using System.Collections.Concurrent;
using System.IO;
using System.Threading.Channels;
using BtConnect.Core;
using InTheHand.Net.Bluetooth;
using InTheHand.Net.Sockets;

namespace BtConnect.Server;

public sealed class BluetoothHost : IAsyncDisposable
{
    public BroadcastHub Hub { get; } = new();
    public event Action<string>? Message;
    private BluetoothListener? listener;
    private BluetoothRadio? radio;
    private RadioMode previousMode;
    private bool restoreRadioMode;
    private readonly CancellationTokenSource lifetime = new();
    private readonly ConcurrentDictionary<BluetoothClient, Task> sessions = new();
    private Task? acceptTask;

    public string Start()
    {
        var stage = "finding the Bluetooth adapter";
        try
        {
            try { radio = BluetoothRadio.Default; }
            catch (PlatformNotSupportedException e)
            {
                throw new InvalidOperationException("Windows could not access a Bluetooth Classic adapter. Turn Bluetooth on in Settings > Bluetooth & devices. If there is no Bluetooth switch, check the adapter/driver in Device Manager or connect a Bluetooth Classic USB adapter.", e);
            }
            if (radio is null) throw new InvalidOperationException("No Bluetooth adapter found. Enable Bluetooth in Windows Settings.");
            var name = radio.Name;
            stage = "reading the adapter state";
            previousMode = radio.Mode;
            stage = "making the computer discoverable";
            restoreRadioMode = true;
            radio.Mode = RadioMode.Discoverable;
            stage = "opening the Bluetooth RFCOMM service";
            listener = new BluetoothListener(Protocol.ServiceId) { ServiceName = "BT Connect" };
            listener.Start();
            acceptTask = Task.Run(AcceptLoop);
            return name;
        }
        catch (Exception e)
        {
            try { listener?.Stop(); } catch (Exception cleanup) { Message?.Invoke("Listener cleanup: " + cleanup.Message); }
            RestoreRadio();
            throw new InvalidOperationException($"Bluetooth startup failed while {stage}: {e.Message}", e);
        }
    }
    private async Task AcceptLoop()
    {
        try
        {
            while (!lifetime.IsCancellationRequested)
            {
                var client = listener!.AcceptBluetoothClient();
                // A start gate prevents a fast disconnect from racing dictionary insertion.
                var ready = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
                var task = Task.Run(async () => { await ready.Task; await Serve(client); });
                sessions[client] = task; ready.SetResult();
            }
        }
        catch (Exception e) when (lifetime.IsCancellationRequested) { _ = e; }
        catch (Exception e) { Message?.Invoke("Bluetooth listener failed: " + e.Message); }
        await Task.CompletedTask;
    }
    private async Task Serve(BluetoothClient client)
    {
        Guid? id = null;
        using var cancel = CancellationTokenSource.CreateLinkedTokenSource(lifetime.Token);
        var output = Channel.CreateBounded<Frame>(new BoundedChannelOptions(64) { SingleReader = true, FullMode = BoundedChannelFullMode.Wait });
        Task? writer = null;
        try
        {
            var stream = client.GetStream();
            using var handshake = CancellationTokenSource.CreateLinkedTokenSource(cancel.Token);
            handshake.CancelAfter(TimeSpan.FromSeconds(10));
            // Closing the socket also interrupts Bluetooth stacks that ignore async cancellation.
            using var registration = handshake.Token.Register(client.Close);
            var hello = await Protocol.ReadAsync(stream, handshake.Token);
            if (hello?.Type != FrameType.Hello) throw new InvalidDataException("Expected a client greeting.");
            handshake.CancelAfter(Timeout.InfiniteTimeSpan);
            var name = Protocol.ReadText(hello).Trim();
            if (string.IsNullOrWhiteSpace(name)) throw new InvalidDataException("Empty client name.");
            writer = Task.Run(async () =>
            {
                try { await foreach (var frame in output.Reader.ReadAllAsync(cancel.Token)) await Protocol.WriteAsync(stream, frame, cancel.Token); }
                finally { cancel.Cancel(); client.Close(); }
            });
            id = Hub.Join(name, frame =>
            {
                if (!output.Writer.TryWrite(frame) && frame.Type != FrameType.Audio)
                { cancel.Cancel(); client.Close(); }
                // Drop audio for slow receivers instead of building unbounded latency.
            });
            Message?.Invoke(name + " connected.");
            while (await Protocol.ReadAsync(stream, cancel.Token) is { } frame)
            {
                if (frame.Type != FrameType.Chat) throw new InvalidDataException("Clients may only send chat after greeting.");
                var text = Protocol.ReadText(frame).Trim();
                if (text.Length == 0) continue;
                // Keep the server-added name inside the protocol's UTF-8 bound.
                var decorated = name + ": " + text;
                if (System.Text.Encoding.UTF8.GetByteCount(decorated) > Protocol.MaxTextBytes)
                    throw new InvalidDataException("Message too long.");
                Hub.Chat(decorated); Message?.Invoke(decorated);
            }
        }
        catch (Exception e) when (e is IOException or OperationCanceledException or System.Net.Sockets.SocketException or ObjectDisposedException or System.Text.DecoderFallbackException)
        { if (!lifetime.IsCancellationRequested && e is not OperationCanceledException) Message?.Invoke("Device disconnected: " + e.Message); }
        finally
        {
            cancel.Cancel(); output.Writer.TryComplete(); client.Close();
            if (id.HasValue) Hub.Leave(id.Value);
            if (writer is not null) { try { await writer; } catch (Exception e) { _ = e; } }
            sessions.TryRemove(client, out _); client.Dispose();
        }
    }
    public async ValueTask DisposeAsync()
    {
        lifetime.Cancel(); Hub.StopAudio(); listener?.Stop();
        foreach (var client in sessions.Keys) client.Close();
        if (acceptTask is not null) await acceptTask;
        await Task.WhenAll(sessions.Values);
        RestoreRadio();
        lifetime.Dispose();
    }
    private void RestoreRadio()
    {
        if (!restoreRadioMode || radio is null) return;
        restoreRadioMode = false;
        try { radio.Mode = previousMode; }
        catch (Exception e) { Message?.Invoke("Could not restore Bluetooth discoverability: " + e.Message); }
    }
}
