using System.ComponentModel;
using System.Windows;
using System.Windows.Input;
using BtConnect.Core;
using NAudio.Wave;

namespace BtConnect.Server;

public partial class MainWindow : Window
{
    private BluetoothHost? host;
    private WaveInEvent? microphone;
    private bool closing;
    public MainWindow() { InitializeComponent(); Closing += OnClosing; }
    private void Log(string text)
    {
        Dispatcher.InvokeAsync(() =>
        {
            ChatLog.Items.Add($"{DateTime.Now:HH:mm}  {text}");
            if (ChatLog.Items.Count > 500) ChatLog.Items.RemoveAt(0);
            ChatLog.ScrollIntoView(ChatLog.Items[^1]);
        });
    }
    private async void ToggleServer(object sender, RoutedEventArgs e)
    {
        ServerButton.IsEnabled = false;
        try
        {
            if (host is not null) { await StopServer(); return; }
            var next = new BluetoothHost();
            next.Message += Log;
            next.Hub.ClientsChanged += names => Dispatcher.InvokeAsync(() =>
            { ConnectionCount.Text = $"{names.Length} devices connected"; DeviceNames.Text = string.Join(", ", names); });
            try { ServerStatus.Text = "Discoverable as " + next.Start(); }
            catch { await next.DisposeAsync(); throw; }
            host = next;
            ServerButton.Content = "Stop server"; AudioButton.IsEnabled = SendButton.IsEnabled = true;
            Log("Server started. Pair the phone in Windows Bluetooth Settings if prompted.");
        }
        catch (Exception ex) { Log(ex.Message); ServerStatus.Text = "Could not start server"; }
        finally { ServerButton.IsEnabled = true; }
    }
    private async Task StopServer()
    {
        StopAudio();
        var old = host; host = null;
        if (old is not null) await old.DisposeAsync();
        ServerStatus.Text = "Server stopped"; ServerButton.Content = "Start server";
        AudioButton.IsEnabled = SendButton.IsEnabled = false;
    }
    private void ToggleAudio(object sender, RoutedEventArgs e)
    {
        if (microphone is not null) { StopAudio(); return; }
        if (host is null) return;
        try
        {
            if (WaveIn.DeviceCount == 0) throw new InvalidOperationException("No microphone found. Check microphone access in Windows Settings.");
            var activeHost = host;
            microphone = new WaveInEvent { DeviceNumber = -1, WaveFormat = new WaveFormat(Protocol.SampleRate, 16, 1), BufferMilliseconds = 40, NumberOfBuffers = 3 };
            microphone.DataAvailable += (_, args) => activeHost.Hub.Audio(args.Buffer.AsSpan(0, args.BytesRecorded).ToArray());
            microphone.RecordingStopped += (_, args) => Dispatcher.InvokeAsync(() =>
            {
                if (args.Exception is not null) { Log("Audio capture stopped: " + args.Exception.Message); StopAudio(); }
            });
            host.Hub.StartAudio(); microphone.StartRecording();
            AudioButton.Content = "Stop broadcasting"; AudioStatus.Text = "Broadcasting audio";
        }
        catch (Exception ex) { StopAudio(); Log(ex.Message); }
    }
    private void StopAudio()
    {
        var old = microphone; microphone = null;
        old?.StopRecording(); old?.Dispose(); host?.Hub.StopAudio();
        AudioButton.Content = "Broadcast microphone"; AudioStatus.Text = "Chat only";
    }
    private void SendMessage(object sender, RoutedEventArgs e)
    {
        var text = MessageInput.Text.Trim();
        if (host is null || text.Length == 0) return;
        try { host.Hub.Chat("Computer: " + text); Log("Computer: " + text); MessageInput.Clear(); }
        catch (Exception ex) { Log(ex.Message); }
    }
    private void MessageKeyDown(object sender, KeyEventArgs e) { if (e.Key == Key.Enter) SendMessage(sender, e); }
    private async void OnClosing(object? sender, CancelEventArgs e)
    {
        if (closing) return;
        e.Cancel = true; IsEnabled = false;
        try { await StopServer(); }
        finally { closing = true; Close(); }
    }
}
