using System.Buffers.Binary;
using System.Text;

namespace BtConnect.Core;

public enum FrameType : byte { Hello = 1, Chat = 2, Count = 3, AudioStart = 4, Audio = 5, AudioStop = 6 }
public sealed record Frame(FrameType Type, byte[] Payload);

public static class Protocol
{
    public static readonly Guid ServiceId = new("84c39d30-2f4b-4d7f-9d81-40c18b587830");
    public const int MaxPayload = 16384;
    public const int MaxTextBytes = 2048;
    public const int SampleRate = 16000;
    private static readonly UTF8Encoding Utf8 = new(false, true);

    public static Frame Text(FrameType type, string text)
    {
        var frame = new Frame(type, Utf8.GetBytes(text));
        Validate(frame);
        return frame;
    }
    public static string ReadText(Frame frame) { Validate(frame); return Utf8.GetString(frame.Payload); }
    public static Frame Count(int count)
    {
        if (count < 0) throw new ArgumentOutOfRangeException(nameof(count));
        var data = new byte[4]; BinaryPrimitives.WriteInt32LittleEndian(data, count);
        return new(FrameType.Count, data);
    }
    public static Frame AudioStart()
    {
        var data = new byte[8];
        BinaryPrimitives.WriteInt32LittleEndian(data, SampleRate);
        BinaryPrimitives.WriteInt16LittleEndian(data.AsSpan(4), 1);
        BinaryPrimitives.WriteInt16LittleEndian(data.AsSpan(6), 16);
        return new(FrameType.AudioStart, data);
    }
    public static void Validate(Frame frame)
    {
        var data = frame.Payload;
        bool valid = frame.Type switch
        {
            FrameType.Hello => data.Length is > 0 and <= 128,
            FrameType.Chat => data.Length is > 0 and <= MaxTextBytes,
            FrameType.Count => data.Length == 4 && BinaryPrimitives.ReadInt32LittleEndian(data) >= 0,
            FrameType.AudioStart => data.Length == 8 && BinaryPrimitives.ReadInt32LittleEndian(data) == SampleRate
                && BinaryPrimitives.ReadInt16LittleEndian(data.AsSpan(4)) == 1
                && BinaryPrimitives.ReadInt16LittleEndian(data.AsSpan(6)) == 16,
            FrameType.Audio => data.Length is > 0 and <= 4096 && data.Length % 2 == 0,
            FrameType.AudioStop => data.Length == 0,
            _ => false
        };
        if (!valid) throw new InvalidDataException("Invalid Bluetooth frame.");
        if (frame.Type is FrameType.Chat or FrameType.Hello) _ = Utf8.GetString(data);
    }
    public static async ValueTask WriteAsync(Stream stream, Frame frame, CancellationToken token = default)
    {
        Validate(frame);
        byte[] header = [0x42, 0x54, 1, (byte)frame.Type, 0, 0, 0, 0];
        BinaryPrimitives.WriteInt32LittleEndian(header.AsSpan(4), frame.Payload.Length);
        await stream.WriteAsync(header, token);
        await stream.WriteAsync(frame.Payload, token);
        await stream.FlushAsync(token);
    }
    public static async ValueTask<Frame?> ReadAsync(Stream stream, CancellationToken token = default)
    {
        var header = new byte[8];
        if (await stream.ReadAsync(header.AsMemory(0, 1), token) == 0) return null;
        await stream.ReadExactlyAsync(header.AsMemory(1), token);
        if (header[0] != 0x42 || header[1] != 0x54 || header[2] != 1)
            throw new InvalidDataException("Unsupported protocol.");
        var size = BinaryPrimitives.ReadInt32LittleEndian(header.AsSpan(4));
        if (size < 0 || size > MaxPayload) throw new InvalidDataException("Frame too large.");
        var data = new byte[size]; await stream.ReadExactlyAsync(data, token);
        var frame = new Frame((FrameType)header[3], data); Validate(frame); return frame;
    }
}
