using System.Runtime.InteropServices;
using System.Text.Json;
using System.Threading.Tasks;
using static HomeControl.Companion.Commands.NativeInput;

namespace HomeControl.Companion.Commands;

/// <summary>
/// Relative mouse movement/clicks via the Win32 <c>SendInput</c> API —
/// injects at the OS input level, so it works regardless of which window
/// has focus, matching the "touchpad mode" the phone app presents.
/// </summary>
internal static class MouseInput
{
    private const uint MouseEventMove = 0x0001;
    private const uint MouseEventLeftDown = 0x0002;
    private const uint MouseEventLeftUp = 0x0004;
    private const uint MouseEventRightDown = 0x0008;
    private const uint MouseEventRightUp = 0x0010;

    public static Task<JsonElement?> HandleMoveAsync(JsonElement? parameters)
    {
        if (parameters is null) return Task.FromResult<JsonElement?>(null);

        var dx = parameters.Value.GetProperty("dx").GetInt32();
        var dy = parameters.Value.GetProperty("dy").GetInt32();

        var input = new Input
        {
            Type = InputMouse,
            Data = new InputUnion { Mouse = new MouseInputData { Dx = dx, Dy = dy, Flags = MouseEventMove } },
        };
        SendInput(1, [input], Marshal.SizeOf<Input>());
        return Task.FromResult<JsonElement?>(null);
    }

    public static Task<JsonElement?> HandleClickAsync(JsonElement? parameters)
    {
        var button = parameters?.GetProperty("button").GetString() ?? "left";
        var (down, up) = button == "right"
            ? (MouseEventRightDown, MouseEventRightUp)
            : (MouseEventLeftDown, MouseEventLeftUp);

        Input[] inputs =
        [
            new Input { Type = InputMouse, Data = new InputUnion { Mouse = new MouseInputData { Flags = down } } },
            new Input { Type = InputMouse, Data = new InputUnion { Mouse = new MouseInputData { Flags = up } } },
        ];
        SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<Input>());
        return Task.FromResult<JsonElement?>(null);
    }
}
