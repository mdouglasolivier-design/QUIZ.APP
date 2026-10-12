import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * Instructor panel: password-protected window to manage the exam.
 * Tabs: Questions (add/edit/delete), Timer (seconds per question),
 * Marks (view all student results, open or clear the marks file).
 */
public class InstructorPanel {

    private static final Font HEAD = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font BODY = new Font("Segoe UI", Font.PLAIN, 13);

    /** Flat secondary action button (quiet, hover-to-accent). */
    private static JButton ghost(String text) {
        JButton b = new JButton(text);
        b.setFont(UiTheme.buttonSmall());
        b.setForeground(UiTheme.MUTED);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new javax.swing.border.EmptyBorder(8, 14, 8, 14));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                if (b.isEnabled()) b.setForeground(UiTheme.ACCENT);
            }

            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                b.setForeground(UiTheme.MUTED);
            }
        });
        return b;
    }

    /** Modern flat table: airy rows, blue selection, subtle header rule. */
    private static JTable themedTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(26);
        table.setFont(BODY);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setBackground(UiTheme.CARD);
        table.getTableHeader().setForeground(UiTheme.MUTED);
        table.setGridColor(UiTheme.BORDER);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setSelectionBackground(new Color(0xDBEAFE));
        table.setSelectionForeground(UiTheme.TEXT);
        table.setFillsViewportHeight(true);
        return table;
    }

    /** Opens the password dialog, then the management window on success. */
    public static void showLogin(JFrame parent) {
        JPasswordField pf = UiTheme.passwordField(20);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        JLabel msg = new JLabel("Enter instructor password:");
        msg.setFont(BODY);
        p.add(msg, BorderLayout.NORTH);
        p.add(pf, BorderLayout.CENTER);

        int ok = JOptionPane.showConfirmDialog(parent, p, "Instructor Login",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        String pass = new String(pf.getPassword());
        if (QuizStore.INSTRUCTOR_PASSWORD.equals(pass)) {
            showPanel(null);
        } else {
            JOptionPane.showMessageDialog(parent, "Wrong password.", "Access denied",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** The management window with three tabs. */
    public static void showPanel(JFrame parent) {
        JTabbedPane tabs = new JTabbedPane() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UiTheme.BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        tabs.setFont(HEAD);
        tabs.addTab("Questions", buildQuestionsTab());
        tabs.addTab("Timer", buildTimerTab());
        tabs.addTab("Marks", buildMarksTab());
        tabs.addTab("Email", buildEmailTab());
        tabs.addTab("Email Log", buildEmailLogTab());

        JDialog d = new JDialog(parent, "Instructor Panel", true);
        d.setContentPane(tabs);
        d.setSize(760, 560);
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
    }

    // ------------------------------------------------------------------
    // Tab 1: manage exam questions (edit questions.csv)
    // ------------------------------------------------------------------
    private static JPanel buildQuestionsTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel hint = new JLabel("Format per line: question,option1,option2,option3,option4,correctNumber(1-4)");
        hint.setFont(BODY);
        p.add(hint, BorderLayout.NORTH);

        JTextArea area = new JTextArea();
        area.setFont(UiTheme.mono());
        area.setBackground(UiTheme.FIELD_BG);
        area.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream("questions.csv"), java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            area.setText(sb.toString());
        } catch (IOException ignored) {
        }

        p.add(new JScrollPane(area), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton save = UiTheme.primaryButton("Save Questions");
        save.addActionListener(e -> {
            try (java.io.PrintWriter w = new java.io.PrintWriter(new java.io.OutputStreamWriter(
                    new FileOutputStream("questions.csv"), java.nio.charset.StandardCharsets.UTF_8))) {
                w.print(area.getText());
                JOptionPane.showMessageDialog(p, "Questions saved.", "Saved",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(p, "Could not save: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        buttons.add(save);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    // ------------------------------------------------------------------
    // Tab 2: timer settings (settings.txt)
    // ------------------------------------------------------------------
    private static JPanel buildTimerTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.anchor = GridBagConstraints.WEST;

        int secs = QuizStore.loadSecondsPerQuestion();

        c.gridx = 0; c.gridy = 0;
        p.add(new JLabel("Seconds per question:"), c);

        JSpinner spinner = new JSpinner(new SpinnerNumberModel(secs, 5, 600, 5));
        spinner.setFont(HEAD);
        c.gridx = 1;
        p.add(spinner, c);

        c.gridx = 0; c.gridy = 1; c.gridwidth = 2;
        JButton save = UiTheme.primaryButton("Save Timer");
        save.addActionListener(e -> {
            int v = (Integer) spinner.getValue();
            QuizStore.saveSecondsPerQuestion(v);
            JOptionPane.showMessageDialog(p, "Timer set to " + v + " seconds per question.",
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        });
        p.add(save, c);

        c.gridy = 2;
        JLabel note = new JLabel("Used by both the graphical quiz and the console quiz.");
        note.setFont(BODY);
        p.add(note, c);

        return p;
    }

    // ------------------------------------------------------------------
    // Tab 3: student marks (results.csv)
    // ------------------------------------------------------------------
    private static JPanel buildMarksTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        List<String[]> rows = QuizStore.loadResults();
        String[] cols = {"Student", "Email", "Score", "Total", "%", "Grade", "Time (s)", "Date", "Time", "Mode"};

        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (String[] r : rows) model.addRow(r);

        JTable table = themedTable(model);
        p.add(new JScrollPane(table), BorderLayout.CENTER);


        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton open = ghost("Open in Excel");
        open.addActionListener(e -> openFile(QuizStore.RESULTS_FILE));
        JButton resend = ghost("Resend Certificate");
        resend.setToolTipText("Re-email the certificate to the selected student");
        resend.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(p, "Select a student in the table first.",
                        "No selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int row = table.convertRowIndexToModel(viewRow);
            String name = String.valueOf(model.getValueAt(row, 0));
            String email = String.valueOf(model.getValueAt(row, 1));
            if (!QuizStore.isValidEmail(email)) {
                JOptionPane.showMessageDialog(p,
                        "This attempt has no valid email saved (older records may lack it).\n"
                        + "The student can retake the quiz with their email address.",
                        "No email on record", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int totalR = Integer.parseInt(String.valueOf(model.getValueAt(row, 3)));
            int scoreR = Integer.parseInt(String.valueOf(model.getValueAt(row, 2)));
            int pctR = totalR == 0 ? 0 : scoreR * 100 / totalR;
            resendCertificate(p, name, email, scoreR, totalR, pctR);
        });
        JButton clear = new JButton("Clear All Marks") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UiTheme.DANGER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        clear.setFont(UiTheme.buttonSmall());
        clear.setForeground(Color.WHITE);
        clear.setBorderPainted(false);
        clear.setFocusPainted(false);
        clear.setContentAreaFilled(false);
        clear.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clear.setBorder(new javax.swing.border.EmptyBorder(8, 16, 8, 16));
        clear.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(p,
                    "Delete ALL saved marks? This cannot be undone.",
                    "Confirm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                new File(QuizStore.RESULTS_FILE).delete();
                model.setRowCount(0);
            }
        });
        buttons.add(open);
        buttons.add(resend);
        buttons.add(clear);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    /** Generates and re-sends a certificate for a past attempt, in the background. */
    private static void resendCertificate(Component parent, String name, String email,
                                          int score, int total, int pct) {
        java.io.File cert = CertificateMaker.makeCertificate(name, score, total,
                pct, QuizStore.gradeFor(pct), QuizStore.today());
        if (cert == null) {
            JOptionPane.showMessageDialog(parent, "Could not create the certificate file.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        final java.io.File f = cert;
        new Thread(() -> {
            EmailSender.SendResult r = EmailSender.sendCertificate(
                    QuizStore.loadEmailSettings(), email, name, score, total,
                    QuizStore.gradeFor(pct), f);
            QuizStore.logEmail(r.ok ? "SENT (resend)" : "FAILED (resend)", email, name,
                    score, total, r.message);
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent, r.message,
                    r.ok ? "Sent" : "Send failed",
                    r.ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE));
        }, "resend-certificate").start();
    }

    // ------------------------------------------------------------------
    // Tab 5: email log - every delivery attempt, newest first
    // ------------------------------------------------------------------
    private static JPanel buildEmailLogTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        List<String[]> rows = QuizStore.loadEmailLog();
        java.util.Collections.reverse(rows); // newest first
        String[] cols = {"Status", "Sent to", "Student", "Score", "Total", "Date", "Time", "Detail"};

        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (String[] r : rows) model.addRow(r);

        JTable table = new JTable(model) {
            @Override public java.awt.Component prepareRenderer(
                    javax.swing.table.TableCellRenderer renderer, int row, int column) {
                java.awt.Component c = super.prepareRenderer(renderer, row, column);
                String status = String.valueOf(getModel().getValueAt(convertRowIndexToModel(row), 0));
                if (status.startsWith("SENT")) {
                    c.setForeground(new Color(21, 128, 61));
                } else if (status.startsWith("FAILED")) {
                    c.setForeground(new Color(185, 28, 28));
                } else {
                    c.setForeground(java.awt.Color.BLACK);
                }
                return c;
            }
        };
        table.setRowHeight(24);
        table.setFont(BODY);
        table.getTableHeader().setFont(HEAD);
        table.setShowGrid(false);
        table.setFillsViewportHeight(true);
        p.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton resendSel = ghost("Resend to Selected");
        resendSel.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(p, "Select a log entry first.",
                        "No selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int row = table.convertRowIndexToModel(viewRow);
            String email = String.valueOf(model.getValueAt(row, 1));
            String name = String.valueOf(model.getValueAt(row, 2));
            int scoreR = Integer.parseInt(String.valueOf(model.getValueAt(row, 3)));
            int totalR = Integer.parseInt(String.valueOf(model.getValueAt(row, 4)));
            int pctR = totalR == 0 ? 0 : scoreR * 100 / totalR;
            resendCertificate(p, name, email, scoreR, totalR, pctR);
        });
        JButton open = ghost("Open Log in Excel");
        open.addActionListener(e -> openFile(QuizStore.EMAIL_LOG_FILE));
        buttons.add(resendSel);
        buttons.add(open);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    private static void openFile(String path) {
        File f = new File(path);
        if (!f.exists()) {
            JOptionPane.showMessageDialog(null, "No marks file yet - students have not taken the quiz.",
                    "Nothing to open", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(f.getAbsoluteFile());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Could not open " + path + ": " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------------
    // Tab 4: email (SMTP) settings for sending certificates
    // ------------------------------------------------------------------
    private static JPanel buildEmailTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        java.util.Properties cfg = QuizStore.loadEmailSettings();

        JTextField hostF = UiTheme.textField(24);
        JTextField portF = UiTheme.textField(6);
        JCheckBox sslBox = new JCheckBox("Use SSL (port 465). Uncheck for STARTTLS (587/25)",
                Boolean.parseBoolean(cfg.getProperty("smtp.ssl", "true")));
        JTextField userF = UiTheme.textField(24);
        JPasswordField passF = UiTheme.passwordField(24);
        JTextField fromF = UiTheme.textField(24);

        int y = 0;
        c.gridx = 0; c.gridy = y;
        p.add(new JLabel("SMTP server:"), c);
        c.gridx = 1;
        p.add(hostF, c);

        c.gridx = 0; c.gridy = ++y;
        p.add(new JLabel("Port:"), c);
        c.gridx = 1;
        p.add(portF, c);

        c.gridx = 0; c.gridy = ++y; c.gridwidth = 2;
        p.add(sslBox, c);
        c.gridwidth = 1;

        c.gridx = 0; c.gridy = ++y;
        p.add(new JLabel("Email username:"), c);
        c.gridx = 1;
        p.add(userF, c);

        c.gridx = 0; c.gridy = ++y;
        p.add(new JLabel("Email password:"), c);
        c.gridx = 1;
        p.add(passF, c);

        c.gridx = 0; c.gridy = ++y;
        p.add(new JLabel("From address:"), c);
        c.gridx = 1;
        p.add(fromF, c);

        c.gridx = 0; c.gridy = ++y; c.gridwidth = 2;
        JLabel note = new JLabel("Tip: for Gmail use smtp.gmail.com, port 465, SSL on, and an App Password.");
        note.setFont(BODY);
        p.add(note, c);

        JButton save = UiTheme.primaryButton("Save Email Settings");
        c.gridy = ++y;
        p.add(save, c);
        c.gridwidth = 1;

        JTextField testTo = UiTheme.textField(20);
        JButton testBtn = UiTheme.primaryButton("Send Test Email");
        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        testRow.add(new JLabel("Send test to:"));
        testRow.add(testTo);
        testRow.add(testBtn);
        c.gridx = 0; c.gridy = ++y; c.gridwidth = 2;
        p.add(testRow, c);
        c.gridwidth = 1;

        save.addActionListener(e -> {
            QuizStore.saveEmailSettings(hostF.getText(), portF.getText(),
                    sslBox.isSelected(), userF.getText(), new String(passF.getPassword()),
                    fromF.getText());
            JOptionPane.showMessageDialog(p, "Email settings saved.", "Saved",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        testBtn.addActionListener(e -> {
            QuizStore.saveEmailSettings(hostF.getText(), portF.getText(),
                    sslBox.isSelected(), userF.getText(), new String(passF.getPassword()),
                    fromF.getText());
            String to = testTo.getText().trim();
            if (!QuizStore.isValidEmail(to)) {
                JOptionPane.showMessageDialog(p, "Enter a valid test email address.",
                        "Check address", JOptionPane.WARNING_MESSAGE);
                return;
            }
            testBtn.setEnabled(false);
            testBtn.setText("Sending...");
            new Thread(() -> {
                EmailSender.SendResult r = EmailSender.sendCertificate(
                        QuizStore.loadEmailSettings(), to, "Test Teacher", 0, 0, "N", null);
                SwingUtilities.invokeLater(() -> {
                    testBtn.setEnabled(true);
                    testBtn.setText("Send Test Email");
                    JOptionPane.showMessageDialog(p, r.message,
                            r.ok ? "Sent" : "Send failed",
                            r.ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                });
            }, "test-email").start();
        });

        return p;
    }
}

