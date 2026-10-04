using System.Buffers.Binary;
using BtConnect.Core;
using Xunit;

namespace BtConnect.Core.Tests;

public sealed class BroadcastHubTests
{
    [Fact]
    public void JoinAndLeaveUpdateEveryClientCount()
    {
        var hub = new BroadcastHub(); var a = new List<Frame>(); var b = new List<Frame>();
        var first = hub.Join("One", a.Add); var second = hub.Join("Two", b.Add);
        Assert.Equal(2, hub.Count); Assert.Equal(["One", "Two"], hub.Names);
        Assert.Equal(2, BinaryPrimitives.ReadInt32LittleEndian(a[^1].Payload));
        Assert.Equal(2, BinaryPrimitives.ReadInt32LittleEndian(b[^1].Payload));
        hub.Leave(first); Assert.Equal(1, BinaryPrimitives.ReadInt32LittleEndian(b[^1].Payload));
        hub.Leave(first); Assert.Equal(1, hub.Count);
        hub.Leave(second); Assert.Equal(0, hub.Count);
    }
    [Fact]
    public void ChatReachesAllClientsDuringAudio()
    {
        var hub = new BroadcastHub(); var a = new List<Frame>(); var b = new List<Frame>();
        hub.Join("One", a.Add); hub.Join("Two", b.Add); hub.StartAudio(); hub.Chat("Computer: hello");
        Assert.Equal("Computer: hello", Protocol.ReadText(a[^1])); Assert.Equal(a[^1], b[^1]); Assert.True(hub.Broadcasting);
    }
    [Fact]
    public void LateJoinReceivesAudioFormatBeforePcmAndStopIsIdempotent()
    {
        var hub = new BroadcastHub(); hub.StartAudio(); var received = new List<Frame>();
        hub.Join("Late", received.Add); hub.Audio([0, 0, 255, 127]); hub.StopAudio(); hub.StopAudio(); hub.Audio([0, 0]);
        Assert.Equal([FrameType.AudioStart, FrameType.Count, FrameType.Audio, FrameType.AudioStop], received.Select(f => f.Type));
        Assert.False(hub.Broadcasting);
    }
    [Fact]
    public void NoAudioIsSentBeforeBroadcastStarts()
    {
        var hub = new BroadcastHub(); var frames = new List<Frame>(); hub.Join("Phone", frames.Add); frames.Clear();
        hub.Audio([0, 0]); Assert.Empty(frames); hub.StartAudio(); hub.StartAudio(); Assert.Single(frames);
    }
    [Fact]
    public async Task FunctionalStreamCarriesCountChatAndAudioToTwoReceivers()
    {
        var hub = new BroadcastHub(); var a = new List<Frame>(); var b = new List<Frame>();
        hub.Join("Pixel", a.Add); hub.Join("Galaxy", b.Add); hub.Chat("Computer: welcome");
        hub.StartAudio(); hub.Audio([0, 0, 255, 127]); hub.StopAudio();
        foreach (var frames in new[] { a, b })
        {
            using var transport = new MemoryStream();
            foreach (var frame in frames) await Protocol.WriteAsync(transport, frame);
            transport.Position = 0;
            var decoded = new List<Frame>(); while (await Protocol.ReadAsync(transport) is { } frame) decoded.Add(frame);
            Assert.Contains(decoded, f => f.Type == FrameType.Chat && Protocol.ReadText(f) == "Computer: welcome");
            Assert.Contains(decoded, f => f.Type == FrameType.Audio && f.Payload.SequenceEqual(new byte[] { 0, 0, 255, 127 }));
            Assert.Equal(FrameType.AudioStop, decoded[^1].Type);
        }
    }
}
