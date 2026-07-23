using System;

namespace HomeControl.Companion.Pairing;

internal sealed class PairingCodeEventArgs : EventArgs
{
    public PairingCodeEventArgs(string code, string deviceName, Action<bool> respond)
    {
        Code = code;
        DeviceName = deviceName;
        Respond = respond;
    }

    public string Code { get; }
    public string DeviceName { get; }
    public Action<bool> Respond { get; }
}
