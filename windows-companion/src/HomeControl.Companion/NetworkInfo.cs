using System;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace HomeControl.Companion;

internal static class NetworkInfo
{
    public static string HostName => Environment.MachineName;

    public static IPAddress? GetPrimaryIPv4Address()
    {
        return NetworkInterface.GetAllNetworkInterfaces()
            .Where(nic => nic.OperationalStatus == OperationalStatus.Up
                && nic.NetworkInterfaceType != NetworkInterfaceType.Loopback)
            .SelectMany(nic => nic.GetIPProperties().UnicastAddresses)
            .Select(address => address.Address)
            .FirstOrDefault(address => address.AddressFamily == AddressFamily.InterNetwork);
    }

    public static string GetPrimaryMacAddress()
    {
        var addressBytes = NetworkInterface.GetAllNetworkInterfaces()
            .Where(nic => nic.OperationalStatus == OperationalStatus.Up
                && nic.NetworkInterfaceType != NetworkInterfaceType.Loopback)
            .Select(nic => nic.GetPhysicalAddress().GetAddressBytes())
            .FirstOrDefault(bytes => bytes.Length == 6);

        return addressBytes is null
            ? "00:00:00:00:00:00"
            : string.Join(":", addressBytes.Select(b => b.ToString("X2")));
    }
}
