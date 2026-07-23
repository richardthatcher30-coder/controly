using System.Runtime.InteropServices;
using System.Text.Json;
using System.Threading.Tasks;
using static HomeControl.Companion.Commands.NativeInput;

namespace HomeControl.Companion.Commands;

/// <summary>
/// Key events use the same <c>RemoteKey</c> names as the Kotlin side
/// (`DPAD_UP`, `VOLUME_UP`, ...) so the Android `plugin-windows` can send
/// the enum name directly. Free-form text typing goes through
/// <c>KEYEVENTF_UNICODE</c> instead of a virtual-key mapping, so it isn't
/// limited to whatever characters have a US keyboard scan code.
/// </summary>
internal static class KeyboardInput
{
    public static Task<JsonElement?> HandleTypeTextAsync(JsonElement? parameters)
    {
        var text = parameters?.GetProperty("text").GetString() ?? string.Empty;
        foreach (var character in text)
        {
            SendUnicodeCharacter(character);
        }
        return Task.FromResult<JsonElement?>(null);
    }

    public static Task<JsonElement?> HandleKeyEventAsync(JsonElement? parameters)
    {
        var key = parameters?.GetProperty("key").GetString() ?? string.Empty;
        var virtualKeyCode = VirtualKeyCodeFor(key);
        if (virtualKeyCode is not null)
        {
            SendVirtualKey(virtualKeyCode.Value);
        }
        return Task.FromResult<JsonElement?>(null);
    }

    private static void SendUnicodeCharacter(char character)
    {
        Input[] inputs =
        [
            new Input { Type = InputKeyboard, Data = new InputUnion { Keyboard = new KeyboardInputData { ScanCode = character, Flags = KeyEventUnicode } } },
            new Input { Type = InputKeyboard, Data = new InputUnion { Keyboard = new KeyboardInputData { ScanCode = character, Flags = KeyEventUnicode | KeyEventKeyUp } } },
        ];
        SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<Input>());
    }

    internal static void SendVirtualKey(ushort virtualKeyCode)
    {
        Input[] inputs =
        [
            new Input { Type = InputKeyboard, Data = new InputUnion { Keyboard = new KeyboardInputData { VirtualKeyCode = virtualKeyCode } } },
            new Input { Type = InputKeyboard, Data = new InputUnion { Keyboard = new KeyboardInputData { VirtualKeyCode = virtualKeyCode, Flags = KeyEventKeyUp } } },
        ];
        SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<Input>());
    }

    private static ushort? VirtualKeyCodeFor(string key) => key switch
    {
        "DPAD_UP" => 0x26,
        "DPAD_DOWN" => 0x28,
        "DPAD_LEFT" => 0x25,
        "DPAD_RIGHT" => 0x27,
        "DPAD_CENTER" => 0x0D,
        "BACK" => 0x1B,
        "HOME" => 0x5B,
        "MENU" => 0x5D,
        "PLAY" => 0xB3,
        "PAUSE" => 0xB3,
        "PLAY_PAUSE" => 0xB3,
        "STOP" => 0xB2,
        "FAST_FORWARD" => 0xB0,
        "REWIND" => 0xB1,
        "VOLUME_UP" => 0xAF,
        "VOLUME_DOWN" => 0xAE,
        "MUTE" => 0xAD,
        "BACKSPACE" => 0x08,
        _ => null,
    };
}
