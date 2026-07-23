using System.Drawing;
using System.Windows.Forms;

namespace HomeControl.Companion.AppButtons;

/// <summary>Lets the user confirm/rename the label before an app button is saved — pre-filled from the exe's own file name so accepting the default is the common case.</summary>
internal sealed class AddAppButtonForm : Form
{
    private readonly TextBox _labelBox;

    public string Label => _labelBox.Text.Trim();

    public AddAppButtonForm(string suggestedLabel, string executablePath)
    {
        Text = "Add App Button";
        FormBorderStyle = FormBorderStyle.FixedDialog;
        StartPosition = FormStartPosition.CenterParent;
        MaximizeBox = false;
        MinimizeBox = false;
        ClientSize = new Size(360, 160);
        Icon = AppIcon.Default;
        Font = new Font("Segoe UI", 9F);

        var pathLabel = new Label
        {
            Text = executablePath,
            ForeColor = Color.FromArgb(0x6B, 0x6B, 0x6B),
            AutoEllipsis = true,
            Dock = DockStyle.Top,
            Height = 20,
            Padding = new Padding(16, 12, 16, 0),
        };

        var labelCaption = new Label
        {
            Text = "Button label",
            Dock = DockStyle.Top,
            Height = 24,
            Padding = new Padding(16, 12, 16, 0),
        };

        _labelBox = new TextBox
        {
            Text = suggestedLabel,
            Dock = DockStyle.Top,
            Margin = new Padding(16, 0, 16, 0),
        };
        var labelBoxPanel = new Panel { Dock = DockStyle.Top, Height = 32, Padding = new Padding(16, 0, 16, 0) };
        labelBoxPanel.Controls.Add(_labelBox);
        _labelBox.Dock = DockStyle.Fill;

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

        Controls.Add(labelBoxPanel);
        Controls.Add(labelCaption);
        Controls.Add(pathLabel);
        Controls.Add(buttonPanel);
    }
}
