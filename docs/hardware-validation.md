# Hardware validation

Run this checklist on Windows 10/11 and at least two Android phones before calling
a release hardware-tested. Linux cross-compilation and the unit suites do not test
Windows WPF rendering, adapter drivers, SDP discovery, OS pairing, or actual sound.

1. Start the Windows server with Bluetooth enabled. Confirm its Bluetooth name is
   discoverable. Turn Bluetooth off and try startup again: an actionable error
   should appear instead of a crash.
2. On Android 8–11 enable Location and grant the location permission; on Android
   12+ grant Nearby devices. Deny permission once and verify the explanation.
   Scan: non-BT Connect devices must be excluded.
   With a paired computer, check cached and fresh service discovery. If the automatic
   list is empty, use Connect to paired computer and verify the connection succeeds
   only when that computer runs BT Connect.
3. Pair/confirm prompts, connect phone A, then B. Every screen should show counts
   1 then 2. Disconnect B: Windows and A should show 1. Reconnect B.
4. Send computer and phone chat including emoji. Both phones and the computer
   should show each message once. Verify chat continues while audio is broadcasting.
5. Start microphone broadcasting, speak, and check both phones hear audio and show
   Broadcasting audio. Stop and verify silence and Chat only. Repeat the transition.
6. Join phone B while A is receiving audio. B should hear the stream without waiting
   for another start. Remove the microphone / deny Windows microphone access and
   verify the server reports the failure and returns phones to Chat only.
7. Move one phone out of range and verify the other remains responsive. Return and
   reconnect. Turn Bluetooth off, disconnect while connecting, and cancel pairing:
   no stale connected count or playback should remain.
8. Background or close the phone app: playback must stop and the count must decrease.
   Reopen and reconnect. Exercise competing audio/call focus and confirm it does not
   play over a call.
9. Stop/close the desktop while broadcasting. Phones should stop playback and reset
   counts. Restart the server and reconnect; no prior process should hold the service.
10. Install a release APK, then a later signed version without uninstalling. Check
    update compatibility. Run the standalone EXE on Windows without .NET installed.

Record OS versions, adapter model/driver, Android models, connected phone count,
and observed audio latency/dropouts. Treat maximum client count as measured adapter
capacity, not a fixed guarantee. Test signed release artifacts as well as debug builds.
