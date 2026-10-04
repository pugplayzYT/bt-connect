using System.Buffers.Binary;
using BtConnect.Core;
using Xunit;

namespace BtConnect.Core.Tests;

public sealed class ProtocolTests
{
    [Fact]
    public async Task SharedFixturesRoundTripThroughFragmentedReads()
    {
        foreach (var line in File.ReadAllLines(Path.Combine(AppContext.BaseDirectory, "fixtures.txt")).Where(x => !x.StartsWith('#')))
        {
            var parts = line.Split('|'); var wire = Convert.FromHexString(parts[2]);
            using var fragmented = new FragmentedStream(wire);
            var frame = await Protocol.ReadAsync(fragmented);
            Assert.NotNull(frame); Assert.Equal(int.Parse(parts[1]), (int)frame.Type);
            using var encoded = new MemoryStream(); await Protocol.WriteAsync(encoded, frame);
            Assert.Equal(wire, encoded.ToArray()); Assert.Null(await Protocol.ReadAsync(fragmented));
        }
    }
    [Fact]
    public async Task ConcatenatedFramesRemainSeparate()
    {
        using var stream = new MemoryStream();
        await Protocol.WriteAsync(stream, Protocol.Text(FrameType.Chat, "hello 👋"));
        await Protocol.WriteAsync(stream, Protocol.Count(2));
        stream.Position = 0;
        Assert.Equal("hello 👋", Protocol.ReadText((await Protocol.ReadAsync(stream))!));
        var count = await Protocol.ReadAsync(stream);
        Assert.Equal(2, BinaryPrimitives.ReadInt32LittleEndian(count!.Payload));
        Assert.Null(await Protocol.ReadAsync(stream));
    }
    [Theory]
    [InlineData("4254020600000000")]
    [InlineData("425401ff00000000")]
    [InlineData("42540102ffffffff")]
    [InlineData("4254010201400000")]
    [InlineData("4254010201000000ff")]
    [InlineData("425401050100000000")]
    [InlineData("425401040800000044ac000001001000")]
    [InlineData("4254010304000000ffffffff")]
    public async Task RejectsMalformedFrames(string hex)
    {
        using var stream = new MemoryStream(Convert.FromHexString(hex));
        await Assert.ThrowsAnyAsync<Exception>(async () => await Protocol.ReadAsync(stream));
    }
    [Theory]
    [InlineData("42")]
    [InlineData("42540102050000004869")]
    public async Task RejectsTruncation(string hex)
    {
        using var stream = new MemoryStream(Convert.FromHexString(hex));
        await Assert.ThrowsAsync<EndOfStreamException>(async () => await Protocol.ReadAsync(stream));
    }
    [Fact]
    public void EnforcesUtf8ByteLimit() => Assert.Throws<InvalidDataException>(() => Protocol.Text(FrameType.Chat, new string('é', 1025)));
    [Fact]
    public async Task HonorsCancellation()
    {
        using var cancel = new CancellationTokenSource(); cancel.Cancel();
        using var stream = new MemoryStream([0x42]);
        await Assert.ThrowsAnyAsync<OperationCanceledException>(async () => await Protocol.ReadAsync(stream, cancel.Token));
    }
    private sealed class FragmentedStream(byte[] bytes) : MemoryStream(bytes)
    {
        public override ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default)
            => base.ReadAsync(buffer[..Math.Min(1, buffer.Length)], cancellationToken);
    }
}
