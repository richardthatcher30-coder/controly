using System;
using System.Drawing;
using System.Linq;
using System.Threading;
using System.Windows.Forms;
using HomeControl.Companion.Networking;
using HomeControl.Companion.Pairing;

namespace HomeControl.Companion;

/// <summary>
/// Opened by a plain left-click on the tray icon (right-click still shows
/// the existing context menu). Shows what a phone would need to know to
/// find and recognize this PC, what's currently paired with it, and whether
/// a phone is connected right now — the last one updates live for as long as
/// this window stays open, driven by <see cref="CompanionServer.ConnectionsChanged"/>.
/// </summary>
internal sealed class InfoForm : Form
{
    private static readonly Color BrandDark = Color.FromArgb(0x0E, 0x3B, 0x36);
    private static readonly Color BrandAccent = Color.FromArgb(0x35, 0xA6, 0x82);
    private static readonly Color MutedText = Color.FromArgb(0x6B, 0x6B, 0x6B);

    private readonly CompanionServer _server;
    private readonly PairingManager _pairingManager;
    private readonly SynchronizationContext _uiContext;
    private readonly Label _statusDot;
    private readonly Label _statusText;
    private Label _pairedDevicesHeading = null!;
    private FlowLayoutPanel _pairedDevicesPanel = null!;

    public InfoForm(PairingManager pairingManager, CompanionServer server)
    {
        _server = server;
        _pairingManager = pairingManager;
        _uiContext = SynchronizationContext.Current ?? new SynchronizationContext();

        Text = "Controly Companion";
        FormBorderStyle = FormBorderStyle.FixedDialog;
        StartPosition = FormStartPosition.CenterScreen;
        MaximizeBox = false;
        MinimizeBox = false;
        AutoSize = true;
        AutoSizeMode = AutoSizeMode.GrowAndShrink;
        MinimumSize = new Size(360, 0);
        TopMost = true;
        Icon = AppIcon.Default;
        BackColor = Color.White;
        Font = new Font("Segoe UI", 9F);

        (_statusDot, _statusText, var statusPanel) = BuildStatusPanel();

        // Dock.Top + AutoSize (not Fill) is what makes the window shrink to
        // fit its actual content instead of stretching to whatever fixed
        // size was guessed up front, leaving dead space below a short list.
        var contentStack = new TableLayoutPanel
        {
            Dock = DockStyle.Top,
            ColumnCount = 1,
            AutoSize = true,
            AutoSizeMode = AutoSizeMode.GrowAndShrink,
            Padding = new Padding(20, 16, 20, 12),
        };
        contentStack.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        contentStack.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        contentStack.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        contentStack.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        _pairedDevicesHeading = SectionHeading($"Paired devices ({pairingManager.GetTrustedClients().Count})");
        _pairedDevicesPanel = new FlowLayoutPanel
        {
            AutoSize = true,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false,
        };
        RefreshPairedDevicesPanel();

        contentStack.Controls.Add(statusPanel, 0, 0);
        contentStack.Controls.Add(BuildInfoGrid(), 0, 1);
        contentStack.Controls.Add(_pairedDevicesHeading, 0, 2);
        contentStack.Controls.Add(_pairedDevicesPanel, 0, 3);

        Controls.Add(contentStack);
        Controls.Add(BuildButtonPanel());
        Controls.Add(BuildHeader());

        UpdateStatus();
        server.ConnectionsChanged += OnConnectionsChanged;
        FormClosed += (_, _) => server.ConnectionsChanged -= OnConnectionsChanged;
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
            Text = "Controly Companion",
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

    private (Label dot, Label text, Control panel) BuildStatusPanel()
    {
        var dot = new Label
        {
            Text = "●",
            Font = new Font("Segoe UI", 12F),
            AutoSize = true,
            Margin = new Padding(0, 2, 6, 0),
        };
        var text = new Label
        {
            Font = new Font("Segoe UI", 10F, FontStyle.Bold),
            AutoSize = true,
            Margin = new Padding(0, 4, 0, 0),
        };
        var panel = new FlowLayoutPanel
        {
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            Margin = new Padding(0, 0, 0, 12),
        };
        panel.Controls.Add(dot);
        panel.Controls.Add(text);
        return (dot, text, panel);
    }

    private Control BuildInfoGrid()
    {
        var rows = new (string Label, string Value)[]
        {
            ("Host name", NetworkInfo.HostName),
            ("Local IP address", NetworkInfo.GetPrimaryIPv4Address()?.ToString() ?? "unavailable"),
            ("MAC address", NetworkInfo.GetPrimaryMacAddress()),
            ("Listening port", CompanionServer.Port.ToString()),
        };

        var grid = new TableLayoutPanel
        {
            AutoSize = true,
            ColumnCount = 2,
            Margin = new Padding(0, 0, 0, 4),
        };
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));

        foreach (var (label, value) in rows)
        {
            grid.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            grid.Controls.Add(new Label
            {
                Text = label,
                ForeColor = MutedText,
                AutoSize = true,
                Margin = new Padding(0, 2, 16, 6),
            });
            grid.Controls.Add(new Label
            {
                Text = value,
                Font = new Font(Font, FontStyle.Bold),
                AutoSize = true,
                Margin = new Padding(0, 2, 0, 6),
            });
        }

        return grid;
    }

    private Label SectionHeading(string text) => new()
    {
        Text = text,
        Font = new Font("Segoe UI", 10F, FontStyle.Bold),
        AutoSize = true,
        Margin = new Padding(0, 8, 0, 6),
    };

    private void RefreshPairedDevicesPanel()
    {
        _pairedDevicesPanel.SuspendLayout();
        _pairedDevicesPanel.Controls.Clear();

        var pairedClients = _pairingManager.GetTrustedClients();
        if (pairedClients.Count == 0)
        {
            _pairedDevicesPanel.Controls.Add(new Label
            {
                Text = "No devices paired yet.",
                ForeColor = MutedText,
                AutoSize = true,
            });
        }
        else
        {
            foreach (var client in pairedClients.OrderByDescending(c => c.PairedAt))
            {
                _pairedDevicesPanel.Controls.Add(BuildPairedDeviceRow(client));
            }
        }

        _pairedDevicesPanel.ResumeLayout();
        _pairedDevicesHeading.Text = $"Paired devices ({pairedClients.Count})";
    }

    private Control BuildPairedDeviceRow(TrustedClient client)
    {
        var row = new FlowLayoutPanel
        {
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false,
            Margin = new Padding(0, 0, 0, 4),
        };

        row.Controls.Add(new Label
        {
            Text = $"{client.DeviceName}  —  paired {client.PairedAt:g}",
            AutoSize = true,
            Margin = new Padding(0, 5, 8, 0),
        });

        var unpairButton = new Button { Text = "Unpair", AutoSize = true };
        unpairButton.Click += (_, _) => OnUnpairClicked(client);
        row.Controls.Add(unpairButton);

        return row;
    }

    private void OnUnpairClicked(TrustedClient client)
    {
        var confirm = MessageBox.Show(
            this,
            $"Unpair \"{client.DeviceName}\"? It will need to pair again to reconnect.",
            "Unpair device",
            MessageBoxButtons.YesNo,
            MessageBoxIcon.Question);
        if (confirm != DialogResult.Yes) return;

        _pairingManager.RemoveTrustedClient(client.PublicKeyBase64);
        _server.DisconnectClient(client.PublicKeyBase64);
        RefreshPairedDevicesPanel();
    }

    private Control BuildButtonPanel()
    {
        var buttonPanel = new FlowLayoutPanel
        {
            Dock = DockStyle.Bottom,
            FlowDirection = FlowDirection.RightToLeft,
            Height = 48,
            Padding = new Padding(8),
        };

        var closeButton = new Button { Text = "Close", DialogResult = DialogResult.OK, Width = 90 };
        var logButton = new Button { Text = "Connection log", Width = 130 };
        logButton.Click += (_, _) =>
        {
            using var logForm = new ConnectionLogForm(_server.ConnectionLog);
            logForm.ShowDialog(this);
        };

        buttonPanel.Controls.Add(closeButton);
        buttonPanel.Controls.Add(logButton);
        AcceptButton = closeButton;
        return buttonPanel;
    }

    private void OnConnectionsChanged(object? sender, EventArgs e)
    {
        _uiContext.Post(_ =>
        {
            if (IsDisposed) return;
            UpdateStatus();
        }, null);
    }

    private void UpdateStatus()
    {
        var connectedDevices = _server.ConnectedDeviceNames;
        if (connectedDevices.Count == 0)
        {
            _statusDot.ForeColor = MutedText;
            _statusText.Text = "No device connected";
            _statusText.ForeColor = MutedText;
        }
        else
        {
            _statusDot.ForeColor = BrandAccent;
            _statusText.Text = $"Connected — {string.Join(", ", connectedDevices)}";
            _statusText.ForeColor = BrandDark;
        }
    }
}
