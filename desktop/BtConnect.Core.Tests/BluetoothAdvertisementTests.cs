using BtConnect.Core;
using BtConnect.Server;
using InTheHand.Net.Bluetooth.AttributeIds;
using InTheHand.Net.Bluetooth.Sdp;
using Xunit;

namespace BtConnect.Core.Tests;

public sealed class BluetoothAdvertisementTests
{
    [Fact]
    public void AdvertisementIsVisibleInPublicServiceBrowse()
    {
        var record = BluetoothAdvertisement.Create();
        var groups = record.GetAttributeById(UniversalAttributeId.BrowseGroupList).Value.GetValueAsElementList();
        Assert.Contains(groups, group => group.GetValueAsUuid() == new Guid("00001002-0000-1000-8000-00805f9b34fb"));
    }
    [Fact]
    public void AdvertisementContainsTheClientsServiceUuid()
    {
        var record = BluetoothAdvertisement.Create();
        var classes = record.GetAttributeById(UniversalAttributeId.ServiceClassIdList).Value.GetValueAsElementList();
        Assert.Contains(classes, service => service.GetValueAsUuid() == Protocol.ServiceId);
    }
    [Fact]
    public void ListenerCanAssignRfcommChannelToCustomRecord()
    {
        var record = BluetoothAdvertisement.Create();
        ServiceRecordHelper.SetRfcommChannelNumber(record, 7);
        Assert.Equal(7, ServiceRecordHelper.GetRfcommChannelNumber(record));
        Assert.NotEmpty(record.ToByteArray());
    }
}
