using System;
using System.Drawing;
using System.IO;
using System.Windows.Forms;

namespace HomeControl.Companion.AppButtons;

/// <summary>
/// Where app launch buttons are actually authored — a real Windows file
/// picker to locate the exact .exe, rather than the phone guessing at
/// install paths it has no way to know. The phone only ever displays and
/// launches whatever's added here.
/// </summary>
internal sealed class ManageAppButtonsForm : Form
{
    private static readonly Color BrandDark = Color.FromArgb(0x0E, 0x3B, 0x36);
    private static readonly Color MutedText = Color.FromArgb(0x6B, 0x6B, 0x6B);

    private readonly AppButtonsStore _store;
    private readonly FlowLayoutPanel _listPanel;

    public ManageAppButtonsForm(AppButtonsStore store)
    {
        _store = store;

        Text = "Manage App Buttons";
        FormBorderStyle = FormBorderStyle.Sizable;
        StartPosition = FormStartPosition.CenterScreen;
        MaximizeBox = true;
        MinimizeBox = false;
        ClientSize = new Size(420, 460);
        MinimumSize = new Size(360, 320);
        Icon = AppIcon.Default;
        BackColor = Color.White;
        Font = new Font("Segoe UI", 9F);

        var header = new Panel { Dock = DockStyle.Top, Height = 56, BackColor = BrandDark };
        header.Controls.Add(new Label
        {
            Text = "Manage App Buttons",
            ForeColor = Color.White,
            Font = new Font("Segoe UI", 12F, FontStyle.Bold),
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleLeft,
            Padding = new Padding(20, 0, 0, 0),
        });

        var buttonPanel = new FlowLayoutPanel
        {
            Dock = DockStyle.Bottom,
            FlowDirection = FlowDirection.RightToLeft,
            Height = 52,
            Padding = new Padding(8),
        };
        var closeButton = new Button { Text = "Close", DialogResult = DialogResult.OK, Width = 90 };
        var addAppButton = new Button { Text = "Add app...", Width = 100 };
        addAppButton.Click += (_, _) => OnAddAppClicked();
        var addUrlButton = new Button { Text = "Add URL...", Width = 100 };
        addUrlButton.Click += (_, _) => OnAddUrlClicked();
        buttonPanel.Controls.Add(closeButton);
        buttonPanel.Controls.Add(addAppButton);
        buttonPanel.Controls.Add(addUrlButton);
        AcceptButton = closeButton;

        _listPanel = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false,
            AutoScroll = true,
            Padding = new Padding(16),
        };

        Controls.Add(_listPanel);
        Controls.Add(buttonPanel);
        Controls.Add(header);

        RefreshList();
    }

    private void OnAddAppClicked()
    {
        using var dialog = new OpenFileDialog
        {
            Title = "Locate the application",
            Filter = "Programs (*.exe)|*.exe|All files (*.*)|*.*",
            InitialDirectory = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
            CheckFileExists = true,
        };
        if (dialog.ShowDialog(this) != DialogResult.OK) return;

        var suggestedLabel = Path.GetFileNameWithoutExtension(dialog.FileName);
        using var labelForm = new AddAppButtonForm(suggestedLabel, dialog.FileName);
        if (labelForm.ShowDialog(this) != DialogResult.OK) return;

        var label = labelForm.Label;
        if (string.IsNullOrWhiteSpace(label)) label = suggestedLabel;

        _store.Add(label, dialog.FileName);
        RefreshList();
    }

    private void OnAddUrlClicked()
    {
        using var form = new AddUrlButtonForm();
        if (form.ShowDialog(this) != DialogResult.OK) return;
        if (string.IsNullOrWhiteSpace(form.Url)) return;

        var url = form.Url;
        // A bare "example.com" typed without a scheme would otherwise get
        // interpreted by ShellExecute as a local file/search query instead
        // of a web address.
        if (!url.Contains("://")) url = "https://" + url;

        var label = string.IsNullOrWhiteSpace(form.Label) ? url : form.Label;
        _store.Add(label, url);
        RefreshList();
    }

    private void RefreshList()
    {
        _listPanel.SuspendLayout();
        _listPanel.Controls.Clear();

        var entries = _store.List();
        if (entries.Count == 0)
        {
            _listPanel.Controls.Add(new Label
            {
                Text = "No app buttons yet — click \"Add app...\" to create one.",
                ForeColor = MutedText,
                AutoSize = true,
            });
        }
        else
        {
            foreach (var entry in entries)
            {
                _listPanel.Controls.Add(BuildRow(entry));
            }
        }

        _listPanel.ResumeLayout();
    }

    private Control BuildRow(AppButtonEntry entry)
    {
        const int rowHeight = 44;

        var row = new TableLayoutPanel
        {
            ColumnCount = 3,
            RowCount = 1,
            Height = rowHeight,
            Width = _listPanel.ClientSize.Width - 32,
            Margin = new Padding(0),
        };
        row.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 32));
        row.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        row.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));

        var icon = new PictureBox
        {
            SizeMode = PictureBoxSizeMode.Zoom,
            Width = 24,
            Height = 24,
            Anchor = AnchorStyles.None,
            Image = ExtractIconImage(entry.ExecutablePath),
        };
        var iconHost = new Panel { Dock = DockStyle.Fill };
        iconHost.Controls.Add(icon);
        icon.Location = new Point(4, (rowHeight - icon.Height) / 2 - 4);

        var nameLabel = new Label
        {
            Text = entry.Label,
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleLeft,
            AutoEllipsis = true,
        };
        var tooltip = new ToolTip();
        tooltip.SetToolTip(nameLabel, entry.ExecutablePath);

        var removeButton = new Button { Text = "Remove", Width = 80, Anchor = AnchorStyles.None };
        var removeHost = new Panel { Dock = DockStyle.Fill };
        removeHost.Controls.Add(removeButton);
        removeButton.Location = new Point(0, (rowHeight - removeButton.Height) / 2);
        removeButton.Click += (_, _) =>
        {
            _store.Remove(entry.Id);
            RefreshList();
        };

        row.Controls.Add(iconHost, 0, 0);
        row.Controls.Add(nameLabel, 1, 0);
        row.Controls.Add(removeHost, 2, 0);

        var divider = new Panel { Dock = DockStyle.Bottom, Height = 1, BackColor = Color.FromArgb(0xE0, 0xE0, 0xE0) };
        var wrapper = new Panel { Height = rowHeight + 1, Width = row.Width, Margin = new Padding(0) };
        wrapper.Controls.Add(row);
        wrapper.Controls.Add(divider);
        row.Dock = DockStyle.Top;
        return wrapper;
    }

    /// <summary>The same icon Explorer would show for this file/shortcut, or null for a URL button (nothing to extract an icon from).</summary>
    private static Image? ExtractIconImage(string path)
    {
        try
        {
            using var icon = Icon.ExtractAssociatedIcon(path);
            return icon?.ToBitmap();
        }
        catch (Exception)
        {
            return null;
        }
    }
}
