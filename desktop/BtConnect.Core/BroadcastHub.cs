namespace BtConnect.Core;

// All control and audio frames share this lock so joins and transitions stay ordered.
public sealed class BroadcastHub
{
    private readonly object gate = new();
    private readonly Dictionary<Guid, (string Name, Action<Frame> Send)> clients = new();
    private bool broadcasting;
    public event Action<string[]>? ClientsChanged;
    public int Count { get { lock (gate) return clients.Count; } }
    public string[] Names { get { lock (gate) return clients.Values.Select(c => c.Name).ToArray(); } }
    public bool Broadcasting { get { lock (gate) return broadcasting; } }

    public Guid Join(string name, Action<Frame> send)
    {
        lock (gate)
        {
            var id = Guid.NewGuid(); clients.Add(id, (name, send));
            send(broadcasting ? Protocol.AudioStart() : new(FrameType.AudioStop, []));
            Publish(Protocol.Count(clients.Count));
            ClientsChanged?.Invoke(Names);
            return id;
        }
    }
    public void Leave(Guid id)
    {
        lock (gate)
        {
            if (!clients.Remove(id)) return;
            Publish(Protocol.Count(clients.Count)); ClientsChanged?.Invoke(Names);
        }
    }
    public void Chat(string text) { lock (gate) Publish(Protocol.Text(FrameType.Chat, text)); }
    public void StartAudio()
    {
        lock (gate) { if (broadcasting) return; broadcasting = true; Publish(Protocol.AudioStart()); }
    }
    public void Audio(byte[] pcm)
    {
        var frame = new Frame(FrameType.Audio, pcm); Protocol.Validate(frame);
        lock (gate) { if (broadcasting) Publish(frame); }
    }
    public void StopAudio()
    {
        lock (gate) { if (!broadcasting) return; broadcasting = false; Publish(new(FrameType.AudioStop, [])); }
    }
    private void Publish(Frame frame) { foreach (var client in clients.Values) client.Send(frame); }
}
