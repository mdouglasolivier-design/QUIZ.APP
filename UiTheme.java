import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Shared light & modern design system for the Java Quiz UI.
 * One place defines the palette, fonts, and component factories so every
 * screen (start, quiz, results, instructor panel) looks consistent.
 *
 * Pure JDK only - no external libraries.
 */
public final class UiTheme {

    // ---- palette -------------------------------------------------------
    public static final Color BG        = new Color(0xF5F7FA); // app background (light gray)
    public static final Color CARD      = Color.WHITE;          // card/panel surface
    public static final Color ACCENT    = new Color(0x2563EB); // primary blue
    public static final Color ACCENT_DK = new Color(0x1D4ED8); // primary hover
    public static final Color SUCCESS   = new Color(0x16A34A); // green (correct / primary actions)
    public static final Color SUCCESS_DK= new Color(0x15803D);
    public static final Color DANGER    = new Color(0xDC2626);  // red (wrong / destructive)
    public static final Color WARNING   = new Color(0xF59E0B); // orange timer
    public static final Color TEXT      = new Color(0x1F2937); // primary text
    public static final Color MUTED     = new Color(0x6B7280); // secondary text
    public static final Color BORDER    = new Color(0xE5E7EB); // hairline borders
    public static final Color FIELD_BG  = new Color(0xF9FAFB); // input backgrounds

    // ---- fonts ---------------------------------------------------------
    private static final String FAMILY = "Segoe UI"; // falls back gracefully elsewhere

    public static Font h1()          { return new Font(FAMILY, Font.BOLD, 36); }
    public static Font h2()          { return new Font(FAMILY, Font.BOLD, 24); }
    public static Font h3()          { return new Font(FAMILY, Font.BOLD, 18); }
    public static Font body()        { return new Font(FAMILY, Font.PLAIN, 14); }
    public static Font bodyMedium()  { return new Font(FAMILY, Font.PLAIN, 15); }
    public static Font small()       { return new Font(FAMILY, Font.PLAIN, 12); }
    public static Font smallMuted()  { return new Font(FAMILY, Font.PLAIN, 12); }
    public static Font button()      { return new Font(FAMILY, Font.BOLD, 15); }
    public static Font buttonSmall() { return new Font(FAMILY, Font.PLAIN, 13); }
    public static Font mono()        { return new Font(Font.MONOSPACED, Font.PLAIN, 13); }

    private UiTheme() {}

    /** Round us down when drawing arcs. */
    static int arc(int v) { return v; }

    // ---- helpers -------------------------------------------------------

    /** Standard card surface: white panel with hairline border and padding. */
    public static JPanel card(int padX, int padY) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(padY, padX, padY, padX)));
        return card;
    }

    /**
     * Flat primary button with a subtle hover shift. The first pixel row is
     * painted slightly darker to fake a soft drop shadow without heavy looks.
     */
    public static JButton primaryButton(String text, Color base, Color hover) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                if (getModel().isRollover()) {
                    g2.setColor(new Color(255, 255, 255, 28));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(button());
        b.setForeground(Color.WHITE);
        b.setBackground(base);
        b.setOpaque(false);           // custom paint handles it
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusTraversalKeysEnabled(false);
        b.setHorizontalAlignment(SwingConstants.CENTER);
        b.setBorder(new EmptyBorder(10, 22, 10, 22));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (b.isEnabled()) b.setBackground(hover);
            }

            @Override public void mouseExited(MouseEvent e) {
                b.setBackground(base);
            }
        });
        return b;
    }

    /** Convenience: blue primary button. */
    public static JButton primaryButton(String text) {
        return primaryButton(text, ACCENT, ACCENT_DK);
    }

    /** Convenience: green primary button. */
    public static JButton successButton(String text) {
        return primaryButton(text, SUCCESS, SUCCESS_DK);
    }

    /** Quiet text-style link/button for secondary actions. */
    public static JButton ghostButton(String text) {
        JButton b = new JButton(text);
        b.setFont(buttonSmall());
        b.setForeground(MUTED);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(4, 8, 4, 8));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setForeground(ACCENT); }
            @Override public void mouseExited(MouseEvent e)  { b.setForeground(MUTED); }
        });
        return b;
    }

    /** Modern bordered text field: light fill, rounded, focus accent. */
    public static JTextField textField(int columns) {
        JTextField f = new JTextField(columns) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(FIELD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(hasFocus() ? ACCENT : BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        f.setFont(bodyMedium());
        f.setForeground(TEXT);
        f.setOpaque(false);
        f.setBorder(new EmptyBorder(8, 12, 8, 12));
        f.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        return f;
    }

    /** Centered variant of the standard text field (start screen inputs). */
    public static JTextField centeredTextField(int columns) {
        JTextField f = textField(columns);
        f.setHorizontalAlignment(JTextField.CENTER);
        return f;
    }

    /** Password field matching the text field style. */
    public static JPasswordField passwordField(int columns) {
        JPasswordField f = new JPasswordField(columns);
        f.setFont(bodyMedium());
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(8, 12, 8, 12)));
        return f;
    }

    /** Label helpers ---------------------------------------------------
     * Primary title font/size/color. */
    public static JLabel title(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(h1());
        l.setForeground(TEXT);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    /** Secondary explanatory text. */
    public static JLabel subtitle(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(body());
        l.setForeground(MUTED);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    /** Small muted label. */
    public static JLabel hint(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(small());
        l.setForeground(MUTED);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    /** Card section heading. */
    public static JLabel section(String text) {
        JLabel l = new JLabel(text);
        l.setFont(h3());
        l.setForeground(TEXT);
        return l;
    }

    // ---- app-wide look & feel tweaks ----------------------------------

    /** Apply global UI tweaks once at startup: default fonts, tables, scrollbars. */
    public static void install() {
        Font def = body();
        UIManager.put("Label.font", def);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Button.font", button());
        UIManager.put("TextField.font", bodyMedium());
        UIManager.put("PasswordField.font", bodyMedium());
        UIManager.put("TextArea.font", mono());
        UIManager.put("CheckBox.font", body());
        UIManager.put("RadioButton.font", body());
        UIManager.put("Spinner.font", bodyMedium());
        UIManager.put("TabbedPane.font", buttonSmall());

        // Flat checkbox indicator
        UIManager.put("CheckBox.icon", flatCheckBoxIcon(false));
        UIManager.put("RadioButton.icon", flatCheckBoxIcon(true));

        // Tables: airy rows, blue selection, subtle grid
        UIManager.put("Table.font", body());
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.selectionBackground", new Color(0xDBEAFE));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("TableHeader.font", new Font(FAMILY, Font.BOLD, 13));
        UIManager.put("TableHeader.background", CARD);
        UIManager.put("TableHeader.foreground", MUTED);
        UIManager.put("TableHeader.cellBorder", BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 1, BORDER),
                new EmptyBorder(8, 10, 8, 10)));

        // Option panes get the body font too
        UIManager.put("OptionPane.messageFont", body());
        UIManager.put("OptionPane.buttonFont", buttonSmall());
        UIManager.put("OptionPane.background", CARD);
        UIManager.put("Panel.background", CARD);
        UIManager.put("OptionPane.messageForeground", TEXT);
    }

    /** Simple flat round indicator for checkbox/radio, 16px. */
    private static Icon flatCheckBoxIcon(final boolean radio) {
        return new Icon() {
            @Override public int getIconWidth() { return 16; }
            @Override public int getIconHeight() { return 16; }

            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                boolean selected = c instanceof JToggleButton && ((JToggleButton) c).isSelected();
                if (radio) {
                    g2.setColor(selected ? ACCENT : CARD);
                    g2.fillOval(x, y, 16, 16);
                    g2.setColor(selected ? ACCENT : BORDER);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawOval(x, y, 15, 15);
                    if (selected) {
                        g2.setColor(Color.WHITE);
                        g2.fillOval(x + 5, y + 5, 6, 6);
                    }
                } else {
                    g2.setColor(selected ? ACCENT : CARD);
                    g2.fillRoundRect(x, y, 16, 16, 5, 5);
                    g2.setColor(selected ? ACCENT : BORDER);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(x, y, 15, 15, 5, 5);
                    if (selected) { // check mark
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND,
                                BasicStroke.JOIN_ROUND));
                        g2.drawLine(x + 3, y + 8, x + 6, y + 12);
                        g2.drawLine(x + 6, y + 12, x + 12, y + 4);
                    }
                }
                g2.dispose();
            }
        };
    }

    /** Rounded panel background helper for soft-gray sections. */
    public static JPanel softPanel(LayoutManager layout) {
        JPanel p = new JPanel(layout) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(FIELD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        return p;
    }
}
