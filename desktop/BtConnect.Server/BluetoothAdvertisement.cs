using BtConnect.Core;
using InTheHand.Net.Bluetooth.AttributeIds;
using InTheHand.Net.Bluetooth.Sdp;

namespace BtConnect.Server;

public static class BluetoothAdvertisement
{
    public static ServiceRecord Create()
    {
        var builder = new ServiceRecordBuilder
        {
            ServiceName = "BT Connect",
            ProtocolType = BluetoothProtocolDescriptorType.Rfcomm
        };
        builder.AddServiceClass(Protocol.ServiceId);
        // Android's general SDP browse needs the service in the public browse root.
        builder.AddCustomAttribute(new ServiceAttribute(UniversalAttributeId.BrowseGroupList,
            new ServiceElement(ElementType.ElementSequence,
                new ServiceElement(ElementType.Uuid16, (ushort)0x1002))));
        return builder.ServiceRecord;
    }
}
