using System.Drawing;
using System.Windows.Forms;

namespace HomeControl.Companion.QuickActions;

/// <summary>Toggle which of the fixed quick actions show up in the phone's "Quick Actions" grid — everything's on by default.</summary>
internal sealed class QuickActionsForm : Form
{
    private static readonly Color BrandDark = Color.FromArgb(0x0E, 0x3B, 0x36);

    public QuickActionsForm(QuickActionsStore store)
    {
        Text = "Quick Actions";
        FormBorderStyle = FormBorderStyle.FixedDialog;
        StartPosition = FormStartPosition.CenterScreen;
        MaximizeBox = false;
        MinimizeBox = false;
        AutoSize = true;
        AutoSizeMode = AutoSizeMode.GrowAndShrink;
        MinimumSize = new Size(340, 0);
        Icon = AppIcon.Default;
        BackColor = Color.White;
        Font = new Font("Segoe UI", 9F);

        var header = new Panel { Dock = DockStyle.Top, Height = 56, BackColor = BrandDark };
        header.Controls.Add(new Label
        {
            Text = "Quick Actions",
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
        buttonPanel.Controls.Add(closeButton);
        AcceptButton = closeButton;

        var listPanel = new FlowLayoutPanel
        {
            Dock = DockStyle.Top,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false,
            AutoSize = true,
            AutoSizeMode = AutoSizeMode.GrowAndShrink,
            Padding = new Padding(20, 16, 20, 12),
        };

        foreach (var action in QuickAction.All)
        {
            var checkbox = new CheckBox
            {
                Text = action.Label,
                Checked = store.IsEnabled(action.Id),
                AutoSize = true,
                Margin = new Padding(0, 4, 0, 4),
            };
            checkbox.CheckedChanged += (_, _) => store.SetEnabled(action.Id, checkbox.Checked);
            listPanel.Controls.Add(checkbox);
        }

        Controls.Add(listPanel);
        Controls.Add(buttonPanel);
        Controls.Add(header);
    }
}
