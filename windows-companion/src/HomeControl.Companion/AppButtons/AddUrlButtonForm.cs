using System.Drawing;
using System.Windows.Forms;

namespace HomeControl.Companion.AppButtons;

/// <summary>Same idea as <see cref="AddAppButtonForm"/> but for a URL button — no file to browse to, just a label and an address.</summary>
internal sealed class AddUrlButtonForm : Form
{
    private readonly TextBox _labelBox;
    private readonly TextBox _urlBox;

    public string Label => _labelBox.Text.Trim();
    public string Url => _urlBox.Text.Trim();

    public AddUrlButtonForm()
    {
        Text = "Add URL Button";
        FormBorderStyle = FormBorderStyle.FixedDialog;
        StartPosition = FormStartPosition.CenterParent;
        MaximizeBox = false;
        MinimizeBox = false;
        ClientSize = new Size(360, 180);
        Icon = AppIcon.Default;
        Font = new Font("Segoe UI", 9F);

        var urlCaption = new Label { Text = "URL", Dock = DockStyle.Top, Height = 24, Padding = new Padding(16, 12, 16, 0) };
        _urlBox = new TextBox { Dock = DockStyle.Fill };
        var urlBoxPanel = new Panel { Dock = DockStyle.Top, Height = 32, Padding = new Padding(16, 0, 16, 0) };
        urlBoxPanel.Controls.Add(_urlBox);

        var labelCaption = new Label { Text = "Button label", Dock = DockStyle.Top, Height = 24, Padding = new Padding(16, 12, 16, 0) };
        _labelBox = new TextBox { Dock = DockStyle.Fill };
        var labelBoxPanel = new Panel { Dock = DockStyle.Top, Height = 32, Padding = new Padding(16, 0, 16, 0) };
        labelBoxPanel.Controls.Add(_labelBox);

        var buttonPanel = new FlowLayoutPanel
        {
            Dock = DockStyle.Bottom,
            FlowDirection = FlowDirection.RightToLeft,
            Height = 48,
            Padding = new Padding(8),
        };
        var addButton = new Button { Text = "Add", DialogResult = DialogResult.OK, Width = 90 };
        var cancelButton = new Button { Text = "Cancel", DialogResult = DialogResult.Cancel, Width = 90 };
        buttonPanel.Controls.Add(addButton);
        buttonPanel.Controls.Add(cancelButton);

        AcceptButton = addButton;
        CancelButton = cancelButton;

        // Added in reverse since each is Dock.Top — see the same pattern (and why) in InfoForm.
        Controls.Add(urlBoxPanel);
        Controls.Add(urlCaption);
        Controls.Add(labelBoxPanel);
        Controls.Add(labelCaption);
        Controls.Add(buttonPanel);
    }
}
