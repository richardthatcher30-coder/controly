using System.Drawing;
using System.Windows.Forms;

namespace HomeControl.Companion;

/// <summary>
/// Shows the short verification code derived from this session's ECDH
/// exchange. The user confirms it matches what the phone shows on its own
/// screen — that visual, out-of-band comparison is what actually defeats a
/// man-in-the-middle, not anything sent over the wire.
/// </summary>
internal sealed class PairingCodeForm : Form
{
    public PairingCodeForm(string code, string deviceName)
    {
        Text = "HomeControl Pairing";
        FormBorderStyle = FormBorderStyle.FixedDialog;
        StartPosition = FormStartPosition.CenterScreen;
        MaximizeBox = false;
        MinimizeBox = false;
        ClientSize = new Size(340, 220);
        TopMost = true;
        Icon = AppIcon.Default;

        var messageLabel = new Label
        {
            Text = $"\"{deviceName}\" wants to connect.\nConfirm this code matches your phone:",
            AutoSize = false,
            TextAlign = ContentAlignment.MiddleCenter,
            Dock = DockStyle.Top,
            Height = 60,
            Padding = new Padding(12, 12, 12, 0),
        };

        var codeLabel = new Label
        {
            Text = code,
            Font = new Font(Font.FontFamily, 28, FontStyle.Bold),
            TextAlign = ContentAlignment.MiddleCenter,
            Dock = DockStyle.Top,
            Height = 80,
        };

        var buttonPanel = new FlowLayoutPanel
        {
            Dock = DockStyle.Bottom,
            FlowDirection = FlowDirection.RightToLeft,
            Height = 56,
            Padding = new Padding(12),
        };

        var confirmButton = new Button { Text = "Confirm", DialogResult = DialogResult.OK, Width = 100 };
        var denyButton = new Button { Text = "Deny", DialogResult = DialogResult.Cancel, Width = 100 };
        buttonPanel.Controls.Add(confirmButton);
        buttonPanel.Controls.Add(denyButton);

        AcceptButton = confirmButton;
        CancelButton = denyButton;

        Controls.Add(codeLabel);
        Controls.Add(messageLabel);
        Controls.Add(buttonPanel);
    }
}
