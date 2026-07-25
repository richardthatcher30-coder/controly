using System;
using System.Windows.Forms;
using HomeControl.Companion.AppButtons;
using HomeControl.Companion.Diagnostics;
using HomeControl.Companion.Networking;
using HomeControl.Companion.Pairing;
using HomeControl.Companion.QuickActions;

namespace HomeControl.Companion;

/// <summary>
/// Hosts the tray icon and owns the companion server's lifetime. There is no
/// visible main window — <see cref="ApplicationContext"/> keeps the message
/// loop alive for as long as the tray icon exists. Left-click opens the info
/// window; right-click shows the existing context menu (WinForms wires that
/// up automatically from <see cref="NotifyIcon.ContextMenuStrip"/>).
/// </summary>
internal sealed class TrayApplicationContext : ApplicationContext
{
    // Never shown — exists purely to give background threads (the WebSocket
    // handler) a reliable Invoke/BeginInvoke target on the UI thread.
    // NotifyIcon has no such capability, and SynchronizationContext.Current
    // isn't guaranteed to be installed this early in startup.
    private readonly Form _marshalForm = new();
    private readonly NotifyIcon _trayIcon;
    private readonly PairingManager _pairingManager;
    private readonly CompanionServer _server;
    private bool _wasConnected;

    public TrayApplicationContext()
    {
        _ = _marshalForm.Handle; // force handle creation so Invoke/BeginInvoke work immediately

        _pairingManager = new PairingManager();
        _pairingManager.PairingCodeReady += (_, e) => _marshalForm.BeginInvoke(() => ShowPairingPrompt(e));

        _server = new CompanionServer(_pairingManager, new ConnectionLogStore());
        // Raised from the WebSocket handler's own thread, not the UI thread —
        // BeginInvoke hops it onto the UI thread before touching the tray icon.
        _server.ConnectionsChanged += (_, _) => _marshalForm.BeginInvoke(OnConnectionsChanged);

        _trayIcon = new NotifyIcon
        {
            Icon = AppIcon.Default,
            Visible = true,
            Text = "Controly Companion",
            ContextMenuStrip = BuildContextMenu(),
        };
        _trayIcon.MouseClick += OnTrayIconMouseClick;

        _server.Start();
    }

    private void OnConnectionsChanged()
    {
        var isConnected = _server.ConnectedDeviceNames.Count > 0;
        if (isConnected == _wasConnected) return;
        _wasConnected = isConnected;

        _trayIcon.BalloonTipTitle = "Controly Companion";
        _trayIcon.BalloonTipText = isConnected ? "Mobile Connected" : "Mobile Disconnected";
        _trayIcon.BalloonTipIcon = ToolTipIcon.Info;
        _trayIcon.ShowBalloonTip(3000);
    }

    private ContextMenuStrip BuildContextMenu()
    {
        var menu = new ContextMenuStrip();
        menu.Items.Add("Controly Companion", null, (_, _) => { }).Enabled = false;
        menu.Items.Add(new ToolStripSeparator());

        var startupItem = new ToolStripMenuItem("Start with Windows")
        {
            CheckOnClick = true,
            Checked = StartupManager.IsEnabled(),
        };
        startupItem.CheckedChanged += (_, _) => StartupManager.SetEnabled(startupItem.Checked);
        menu.Items.Add(startupItem);

        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("Info", null, (_, _) => ShowInfoForm());
        menu.Items.Add("Manage app buttons...", null, (_, _) => ShowManageAppButtonsForm());
        menu.Items.Add("Quick actions...", null, (_, _) => ShowQuickActionsForm());
        menu.Items.Add("Support", null, (_, _) => ShowSupportForm());
        menu.Items.Add("Exit", null, OnExitClicked);
        return menu;
    }

    private void OnTrayIconMouseClick(object? sender, MouseEventArgs e)
    {
        if (e.Button == MouseButtons.Left)
        {
            ShowInfoForm();
        }
    }

    private void ShowInfoForm()
    {
        using var infoForm = new InfoForm(_pairingManager, _server);
        infoForm.ShowDialog();
    }

    private void ShowManageAppButtonsForm()
    {
        using var form = new ManageAppButtonsForm(new AppButtonsStore());
        form.ShowDialog();
    }

    private void ShowQuickActionsForm()
    {
        using var form = new QuickActionsForm(new QuickActionsStore());
        form.ShowDialog();
    }

    private void ShowSupportForm()
    {
        using var form = new SupportForm();
        form.ShowDialog();
    }

    private void ShowPairingPrompt(PairingCodeEventArgs e)
    {
        using var form = new PairingCodeForm(e.Code, e.DeviceName);
        var result = form.ShowDialog();
        e.Respond(result == DialogResult.OK);
    }

    private void OnExitClicked(object? sender, EventArgs e)
    {
        _trayIcon.Visible = false;
        _server.Stop();
        _marshalForm.Close();
        Application.Exit();
    }
}
