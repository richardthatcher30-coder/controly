using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;

namespace HomeControl.Companion.AppButtons;

internal static class IconEncoder
{
    private const int IconSize = 48;

    /// <summary>The file/shortcut icon Explorer would show, resized and PNG-encoded as base64 — or null if it can't be resolved (e.g. the target no longer exists).</summary>
    public static string? ExtractBase64(string path)
    {
        try
        {
            using var icon = Icon.ExtractAssociatedIcon(path);
            if (icon is null) return null;

            using var bitmap = new Bitmap(icon.ToBitmap(), new Size(IconSize, IconSize));
            using var pngStream = new MemoryStream();
            bitmap.Save(pngStream, ImageFormat.Png);
            return Convert.ToBase64String(pngStream.ToArray());
        }
        catch (Exception)
        {
            return null;
        }
    }
}
