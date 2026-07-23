using System.Drawing;
using System.Windows.Forms;

namespace HomeControl.Companion;

/// <summary>
/// The same icon set as <c>ApplicationIcon</c> in the csproj (and the mobile
/// app's launcher icon) — extracted from the running exe rather than loaded
/// from a separate file path, so it's always in sync with what's actually
/// embedded in this build and never goes missing at runtime.
/// </summary>
internal static class AppIcon
{
    public static readonly Icon Default = Icon.ExtractAssociatedIcon(Application.ExecutablePath) ?? SystemIcons.Application;
}
