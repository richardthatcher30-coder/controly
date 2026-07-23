using System.Drawing;
using System.Threading;
using System.Windows.Forms;
using HomeControl.Companion.Diagnostics;

namespace HomeControl.Companion;

/// <summary>
/// Live view of pairing/auth attempts — newest first, updates in real time
/// while it's open. Not persisted; see <see cref="ConnectionLogStore"/>.
/// </summary>
internal sealed class ConnectionLogForm : Form
{
    private readonly ConnectionLogStore _connectionLog;
    private readonly ListView _listView;
    private readonly SynchronizationContext _uiContext;

    public ConnectionLogForm(ConnectionLogStore connectionLog)
    {
        _connectionLog = connectionLog;
        _uiContext = SynchronizationContext.Current ?? new SynchronizationContext();

        Text = "Connection Attempts";
        StartPosition = FormStartPosition.CenterParent;
        ClientSize = new Size(560, 400);
        MinimizeBox = false;
        Icon = AppIcon.Default;
        // InfoForm (this window's owner) is itself TopMost, which otherwise
        // keeps it drawn above its own child windows — matching TopMost here
        // is what makes this dialog reliably appear in front instead.
        TopMost = true;

        var buttonPanel = new FlowLayoutPanel
        {
            Dock = DockStyle.Bottom,
            FlowDirection = FlowDirection.RightToLeft,
            Height = 44,
            Padding = new Padding(8),
        };
        var clearButton = new Button { Text = "Clear log", Width = 100 };
        clearButton.Click += (_, _) => connectionLog.Clear();
        buttonPanel.Controls.Add(clearButton);

        _listView = new ListView
        {
            Dock = DockStyle.Fill,
            View = View.Details,
            FullRowSelect = true,
            GridLines = true,
        };
        _listView.Columns.Add("Time", 130);
        _listView.Columns.Add("Device", 140);
        _listView.Columns.Add("Event", 110);
        _listView.Columns.Add("Detail", 160);

        foreach (var entry in connectionLog.Snapshot())
        {
            AddRow(entry);
        }

        Controls.Add(_listView);
        Controls.Add(buttonPanel);

        connectionLog.EntryAdded += OnEntryAdded;
        connectionLog.Cleared += OnCleared;
        FormClosed += (_, _) =>
        {
            connectionLog.EntryAdded -= OnEntryAdded;
            connectionLog.Cleared -= OnCleared;
        };
    }

    private void OnCleared(object? sender, EventArgs e)
    {
        _uiContext.Post(_ =>
        {
            if (IsDisposed) return;
            _listView.Items.Clear();
        }, null);
    }

    private void OnEntryAdded(object? sender, ConnectionLogEntry entry)
    {
        _uiContext.Post(_ =>
        {
            if (IsDisposed) return;
            AddRow(entry, insertAtTop: true);
        }, null);
    }

    private void AddRow(ConnectionLogEntry entry, bool insertAtTop = false)
    {
        var item = new ListViewItem(entry.Timestamp.ToString("g"));
        item.SubItems.Add(entry.DeviceName ?? entry.RemoteAddress);
        item.SubItems.Add(entry.EventType.ToString());
        item.SubItems.Add(entry.Detail);

        if (insertAtTop)
        {
            _listView.Items.Insert(0, item);
        }
        else
        {
            _listView.Items.Add(item);
        }
    }
}
