# Bluetooth protocol v1

BT Connect advertises a Bluetooth Classic RFCOMM SDP service with UUID
`84c39d30-2f4b-4d7f-9d81-40c18b587830` and service name `BT Connect`.
Android discovers nearby computers, checks their SDP UUIDs, and lists only matches.
The service record includes the public browse group UUID `0x1002` so generic SDP
browse can discover the custom service. Android checks paired devices' cached
service UUIDs and accepts delayed SDP responses until the scan is explicitly stopped.
Users can also select a paired computer directly when generic browsing fails;
the RFCOMM connection still targets the same custom service UUID.
Use secure RFCOMM sockets; accept the OS pairing prompts. This is an application
stream, not an A2DP headset profile.

Every frame has an 8-byte header: ASCII `BT`, version byte `1`, type byte,
then a signed 32-bit little-endian payload length. The length must be between
0 and 16384 before allocation. Read the exact length even when the transport
fragments a packet. Unknown types, versions, invalid UTF-8, malformed payloads,
or truncated frames terminate the connection.

| Type | Name | Payload |
| --- | --- | --- |
| 1 | Hello | UTF-8 device name, 1–128 bytes; first client frame |
| 2 | Chat | UTF-8 text, 1–2048 bytes; either direction |
| 3 | Count | Nonnegative int32 little-endian connected-client count |
| 4 | AudioStart | int32 sample rate 16000, int16 channels 1, int16 bits 16, all little-endian |
| 5 | Audio | Signed PCM16 little-endian mono samples; even length, 2–4096 bytes |
| 6 | AudioStop | Empty |

After Hello, clients may only send Chat. The server prefixes phone messages with
the device name, and sends Chat to every connected phone. It sends an initial
AudioStop or AudioStart and a fresh Count when a client joins. AudioStart always
precedes PCM, including late joins; AudioStop switches the phone back to Chat only.
Chat and Count continue during broadcasts. Disconnect resets the phone's count
and playback state. Server Hello timeout: 10 seconds; Android connection timeout:
30 seconds.

Microphone capture uses 40 ms packets (1280 bytes) at 256 kbit/s per phone, plus
framing overhead. A bounded 64-frame queue per client prevents unlimited memory
growth. Audio packets may be dropped for slow receivers; if a control/chat frame
cannot be enqueued, that client is disconnected. This is real-time speech quality;
maximum simultaneous phones depends on the adapter, radio interference, and OS.

`fixtures.txt` is consumed by both C# and Java tests to check wire compatibility.
