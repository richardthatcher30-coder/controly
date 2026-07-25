using System;
using System.Diagnostics;
using System.Drawing;
using System.Reflection;
using System.Windows.Forms;

namespace HomeControl.Companion;

/// <summary>
/// Opened from the tray menu's "Support" item — just the handful of things
/// someone would need when reaching out for help: the installed version,
/// and links to the main site and the dedicated support site.
/// </summary>
internal sealed class SupportForm : Form
{
    private static readonly Color BrandDark = Color.FromArgb(0x0E, 0x3B, 0x36);
    private const string WebsiteUrl = "https://controly.co.uk";
    private const string SupportUrl = "https://support.controly.co.uk";

    public SupportForm()
    {
        Text = "Controly Support";
        FormBorderStyle = FormBorderStyle.FixedDialog;
        StartPosition = FormStartPosition.CenterScreen;
        MaximizeBox = false;
        MinimizeBox = false;
        AutoSize = true;
        AutoSizeMode = AutoSizeMode.GrowAndShrink;
        MinimumSize = new Size(340, 0);
        TopMost = true;
        Icon = AppIcon.Default;
        BackColor = Color.White;
        Font = new Font("Segoe UI", 9F);

        Controls.Add(BuildContent());
        Controls.Add(BuildHeader());
    }

    private Control BuildHeader()
    {
        var header = new Panel
        {
            Dock = DockStyle.Top,
            Height = 64,
            BackColor = BrandDark,
        };
        var titleLabel = new Label
        {
            Text = "Controly Support",
            ForeColor = Color.White,
            Font = new Font("Segoe UI", 13F, FontStyle.Bold),
            AutoSize = false,
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleLeft,
            Padding = new Padding(20, 0, 0, 0),
        };
        header.Controls.Add(titleLabel);
        return header;
    }

    private Control BuildContent()
    {
        var stack = new TableLayoutPanel
        {
            Dock = DockStyle.Top,
            ColumnCount = 1,
            AutoSize = true,
            AutoSizeMode = AutoSizeMode.GrowAndShrink,
            Padding = new Padding(20, 16, 20, 16),
        };
        stack.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        stack.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        stack.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        stack.RowStyles.Add(new RowStyle(SizeType.AutoSize));

        stack.Controls.Add(InfoRow("Version", VersionText()), 0, 0);
        stack.Controls.Add(LinkRow("Website", WebsiteUrl), 0, 1);
        stack.Controls.Add(LinkRow("Support", SupportUrl), 0, 2);
        stack.Controls.Add(BuildButtonPanel(), 0, 3);

        return stack;
    }

    private static string VersionText()
    {
        var version = Assembly.GetExecutingAssembly().GetName().Version;
        return version is null ? "unknown" : $"{version.Major}.{version.Minor}.{version.Build}";
    }

    private Control InfoRow(string label, string value)
    {
        var row = new FlowLayoutPanel
        {
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            Margin = new Padding(0, 0, 0, 10),
        };
        row.Controls.Add(new Label
        {
            Text = $"{label}:",
            AutoSize = true,
            Margin = new Padding(0, 2, 8, 0),
        });
        row.Controls.Add(new Label
        {
            Text = value,
            Font = new Font(Font, FontStyle.Bold),
            AutoSize = true,
            Margin = new Padding(0, 2, 0, 0),
        });
        return row;
    }

    private Control LinkRow(string label, string url)
    {
        var row = new FlowLayoutPanel
        {
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            Margin = new Padding(0, 0, 0, 10),
        };
        row.Controls.Add(new Label
        {
            Text = $"{label}:",
            AutoSize = true,
            Margin = new Padding(0, 2, 8, 0),
        });
        var link = new LinkLabel
        {
            Text = url,
            AutoSize = true,
            Margin = new Padding(0, 2, 0, 0),
        };
        link.LinkClicked += (_, _) => OpenUrl(url);
        row.Controls.Add(link);
        return row;
    }

    private static void OpenUrl(string url)
    {
        Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
    }

    private Control BuildButtonPanel()
    {
        var buttonPanel = new FlowLayoutPanel
        {
            FlowDirection = FlowDirection.RightToLeft,
            AutoSize = true,
            Margin = new Padding(0, 6, 0, 0),
        };
        var closeButton = new Button { Text = "Close", DialogResult = DialogResult.OK, Width = 90 };
        buttonPanel.Controls.Add(closeButton);
        AcceptButton = closeButton;
        return buttonPanel;
    }
}
