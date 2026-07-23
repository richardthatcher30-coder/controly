using System;
using System.Runtime.InteropServices;

namespace HomeControl.Companion.Commands;

/// <summary>
/// Shared, correctly-sized Win32 <c>SendInput</c> structures. The union
/// must be sized to fit its largest member (<c>MOUSEINPUT</c>, 32 bytes on
/// 64-bit) regardless of which member a given call actually populates — a
/// keyboard-only union smaller than the true native <c>INPUT</c> size
/// makes <c>SendInput</c> reject the call outright with
/// <c>ERROR_INVALID_PARAMETER</c> (confirmed: this was exactly why
/// keyboard/volume input silently did nothing while mouse worked — mouse
/// happened to already use the largest union member).
/// </summary>
internal static class NativeInput
{
    public const int InputMouse = 0;
    public const int InputKeyboard = 1;

    public const uint KeyEventKeyUp = 0x0002;
    public const uint KeyEventUnicode = 0x0004;

    [StructLayout(LayoutKind.Sequential)]
    public struct MouseInputData
    {
        public int Dx;
        public int Dy;
        public uint MouseData;
        public uint Flags;
        public uint Time;
        public IntPtr ExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct KeyboardInputData
    {
        public ushort VirtualKeyCode;
        public ushort ScanCode;
        public uint Flags;
        public uint Time;
        public IntPtr ExtraInfo;
    }

    [StructLayout(LayoutKind.Explicit)]
    public struct InputUnion
    {
        [FieldOffset(0)]
        public MouseInputData Mouse;

        [FieldOffset(0)]
        public KeyboardInputData Keyboard;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct Input
    {
        public int Type;
        public InputUnion Data;
    }

    [DllImport("user32.dll", SetLastError = true)]
    public static extern uint SendInput(uint numberOfInputs, Input[] inputs, int inputSize);
}
