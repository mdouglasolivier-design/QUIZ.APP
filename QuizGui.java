import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import javax.swing.*;

/**
 * Java Quiz - graphical version.
 *
 * Screens: Start -> Questions (with a live timer bar) -> Results.
 * Answers are clicked as buttons, so typed input can never collide with the timer.
 *
 * Run:  java QuizGui [questionsFile] [secondsPerQuestion]
 */
public class QuizGui {

    private static int secondsPerQuestion = QuizStore.loadSecondsPerQuestion();
    private static String studentName = "";
    private static String studentEmail = "";
    private static java.io.File lastCertFile; // certificate for the finished attempt

    static class Question {
        final String text;
        final String[] options;
        final int correctIndex; // 0-based

        Question(String text, String[] options, int correctIndex) {
            this.text = text;
            this.options = options;
            this.correctIndex = correctIndex;
        }
    }

    private static final List<Question> questions = new ArrayList<>();
    private static String sourceName = "questions.csv";

    // --- widgets ---
    private static JFrame frame;
    private static CardLayout cards;
    private static JPanel mainPanel;

    private static JTextField nameField;
    private static JTextField emailField;
    private static JLabel emailLabel;
    private static JButton emailBtnRef;
    private static JLabel counterLabel;
    private static JLabel timeLeftLabel;
    private static JProgressBar timerBar;
    private static JLabel questionLabel;
    private static JPanel buttonsPanel;

    private static JLabel bigResult;
    private static JLabel detailResult;
    private static JLabel feedbackLabel;

    // --- game state ---
    private static javax.swing.Timer swingTimer;
    private static long deadline;
    private static long questionStartTime;
    private static boolean answered;
    private static int score;
    private static int index;
    private static int timedOut;
    private static long millisUsed;

    // --- palette (from shared theme) ---
    private static final Color BG = UiTheme.BG;
    private static final Color CARD = UiTheme.CARD;
    private static final Color GREEN = UiTheme.SUCCESS;
    private static final Color RED = UiTheme.DANGER;
    private static final Color ACCENT = UiTheme.ACCENT;
    private static final Color TEXT = UiTheme.TEXT;
    private static final Color MUTED = UiTheme.MUTED;
    private static final Color BORDER = UiTheme.BORDER;

    public static void main(String[] args) {
        String file = args.length > 0 ? args[0] : "questions.csv";
        if (args.length > 1) {
            try {
                secondsPerQuestion = Integer.parseInt(args[1]);
                if (secondsPerQuestion < 1) secondsPerQuestion = 20;
            } catch (NumberFormatException e) {
                System.out.println("Bad time argument: " + args[1] + " - using " + secondsPerQuestion + "s.");
            }
        }

        questions.addAll(loadQuestionsFromCsv(file));
        sourceName = new File(file).getName();

        if (questions.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "No questions were loaded from '" + file + "'.\n\n"
                    + "Each line must be:\n"
                    + "question,option1,option2,option3,option4,correctOptionNumber",
                    "Java Quiz", JOptionPane.ERROR_MESSAGE);
            return;
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                UiTheme.install();
                buildAndShow();
                checkEmailConfiguredOnStartup();
            }
        });
    }

    /** Warn the instructor before students start if email is not configured yet. */
    private static void checkEmailConfiguredOnStartup() {
        if (QuizStore.isEmailConfigured()) return;

        JPasswordField pf = new JPasswordField(20);
        JPanel warn = new JPanel(new BorderLayout(0, 10));
        JLabel msg = new JLabel("<html><div style='width:420px'>"
                + "<b>Email is not configured yet.</b><br><br>"
                + "Certificates are delivered by email, so students cannot receive theirs "
                + "until the mail server is set up.<br><br>"
                + "Enter the instructor password to set it up now (cancel to continue anyway - "
                + "this warning will reappear on next start):</div></html>");
        warn.add(msg, BorderLayout.NORTH);
        warn.add(pf, BorderLayout.CENTER);

        int ok = JOptionPane.showConfirmDialog(frame, warn,
                "Email not configured", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        if (QuizStore.INSTRUCTOR_PASSWORD.equals(new String(pf.getPassword()))) {
            InstructorPanel.showPanel(frame); // opens on the Email tab work flow
        } else {
            JOptionPane.showMessageDialog(frame,
                    "Wrong password. You can set this up later via Instructor > Email.",
                    "Access denied", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void buildAndShow() {
        frame = new JFrame("Java Quiz");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(640, 540);
        frame.setMinimumSize(new Dimension(520, 460));
        frame.setLocationRelativeTo(null);

        cards = new CardLayout();
        mainPanel = new JPanel(cards);
        mainPanel.add(buildStartScreen(), "start");
        mainPanel.add(buildQuizScreen(), "quiz");
        mainPanel.add(buildResultsScreen(), "results");

        frame.setContentPane(mainPanel);
        cards.show(mainPanel, "start");
        frame.setVisible(true);
    }

    // ------------------------------------------------------------------
    // Start screen
    // ------------------------------------------------------------------
    private static JPanel buildStartScreen() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(BG);

        JPanel card = UiTheme.card(40, 34);
        card.setMaximumSize(new Dimension(460, 420));

        JLabel title = UiTheme.title("Java Quiz");

        JLabel subtitle = UiTheme.subtitle(
                "<html><div style='text-align:center'>Enter your name to begin.<br>"
                + "Your certificate is generated at the end (email optional).</div></html>");

        nameField = UiTheme.centeredTextField(16);
        nameField.setMaximumSize(new Dimension(260, 38));
        nameField.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameField.setToolTipText("Your name appears on the certificate");

        emailField = UiTheme.centeredTextField(16);
        emailField.setMaximumSize(new Dimension(260, 38));
        emailField.setAlignmentX(Component.CENTER_ALIGNMENT);
        emailField.setToolTipText("Optional: leave blank to only download your certificate");

        emailLabel = UiTheme.hint("Your email (optional - only if you want it emailed)");

        JLabel info = UiTheme.hint(questions.size() + " questions  |  "
                + secondsPerQuestion + " seconds each");

        JButton start = UiTheme.primaryButton("Start Quiz");
        start.setFont(UiTheme.button());
        start.setAlignmentX(Component.CENTER_ALIGNMENT);
        start.addActionListener(e -> startQuiz());

        JButton consoleBtn = UiTheme.ghostButton("Play in console instead");
        consoleBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        consoleBtn.addActionListener(e -> {
            frame.dispose();
            QuizApp.main(new String[0]);
        });

        JButton instructorBtn = UiTheme.ghostButton("Instructor");
        instructorBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        instructorBtn.addActionListener(e -> InstructorPanel.showLogin(frame));

        card.add(Box.createVerticalStrut(6));
        card.add(title);
        card.add(Box.createVerticalStrut(6));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(12));
        card.add(nameField);
        card.add(Box.createVerticalStrut(10));
        card.add(emailLabel);
        card.add(Box.createVerticalStrut(2));
        card.add(emailField);
        card.add(Box.createVerticalStrut(12));
        card.add(info);
        card.add(Box.createVerticalStrut(14));
        card.add(start);
        card.add(Box.createVerticalStrut(10));
        card.add(consoleBtn);
        card.add(Box.createVerticalStrut(4));
        card.add(instructorBtn);

        outer.add(card);
        return outer;
    }

    // ------------------------------------------------------------------
    // Quiz screen
    // ------------------------------------------------------------------
    private static JPanel buildQuizScreen() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BG);

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setMaximumSize(new Dimension(520, 600));

        // header row: counter + seconds left
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setMaximumSize(new Dimension(520, 22));
        counterLabel = new JLabel("");
        counterLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        counterLabel.setForeground(MUTED);
        timeLeftLabel = new JLabel("");
        timeLeftLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        timeLeftLabel.setForeground(MUTED);
        header.add(counterLabel, BorderLayout.WEST);
        header.add(timeLeftLabel, BorderLayout.EAST);

        // timer bar
        timerBar = new JProgressBar(0, 100);
        timerBar.setStringPainted(false);
        timerBar.setBorderPainted(false);
        timerBar.setForeground(ACCENT);
        timerBar.setBackground(BORDER);
        timerBar.setPreferredSize(new Dimension(520, 8));
        timerBar.setMaximumSize(new Dimension(520, 8));

        // question text
        questionLabel = new JLabel("", SwingConstants.CENTER);
        questionLabel.setFont(new Font("Segoe UI", Font.BOLD, 19));
        questionLabel.setForeground(TEXT);
        questionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // answer buttons
        buttonsPanel = new JPanel(new GridLayout(0, 1, 0, 10));
        buttonsPanel.setOpaque(false);
        buttonsPanel.setMaximumSize(new Dimension(460, 400));

        column.add(header);
        column.add(Box.createVerticalStrut(4));
        column.add(timerBar);
        column.add(Box.createVerticalStrut(26));
        column.add(questionLabel);
        column.add(Box.createVerticalStrut(24));
        column.add(buttonsPanel);

        p.add(column);
        return p;
    }

    // ------------------------------------------------------------------
    // Results screen
    // ------------------------------------------------------------------
    private static JPanel buildResultsScreen() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(BG);

        JPanel card = UiTheme.card(40, 34);
        card.setMaximumSize(new Dimension(460, 460));

        JLabel done = new JLabel("🎉 Quiz Complete!", SwingConstants.CENTER);
        done.setFont(UiTheme.h2());
        done.setForeground(TEXT);
        done.setAlignmentX(Component.CENTER_ALIGNMENT);

        bigResult = new JLabel("", SwingConstants.CENTER);
        bigResult.setFont(new Font("Segoe UI", Font.BOLD, 44));
        bigResult.setForeground(ACCENT);
        bigResult.setAlignmentX(Component.CENTER_ALIGNMENT);

        detailResult = new JLabel("", SwingConstants.CENTER);
        detailResult.setFont(UiTheme.body());
        detailResult.setForeground(MUTED);
        detailResult.setAlignmentX(Component.CENTER_ALIGNMENT);

        feedbackLabel = new JLabel("", SwingConstants.CENTER);
        feedbackLabel.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        feedbackLabel.setForeground(GREEN);
        feedbackLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton viewCert = UiTheme.successButton("View / Download my Certificate");
        viewCert.setAlignmentX(Component.CENTER_ALIGNMENT);
        viewCert.addActionListener(e -> showCertificate());

        JButton emailBtn = UiTheme.primaryButton("Email my Certificate");
        emailBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        emailBtn.addActionListener(e -> emailCertificate());
        emailBtnRef = emailBtn;

        JButton playAgain = UiTheme.primaryButton("Play Again");
        playAgain.setAlignmentX(Component.CENTER_ALIGNMENT);
        playAgain.addActionListener(e -> startQuiz());

        JButton exit = UiTheme.ghostButton("Exit");
        exit.setAlignmentX(Component.CENTER_ALIGNMENT);
        exit.addActionListener(e -> System.exit(0));

        card.add(done);
        card.add(Box.createVerticalStrut(10));
        card.add(bigResult);
        card.add(Box.createVerticalStrut(8));
        card.add(detailResult);
        card.add(Box.createVerticalStrut(6));
        card.add(feedbackLabel);
        card.add(Box.createVerticalStrut(20));
        card.add(viewCert);
        card.add(Box.createVerticalStrut(8));
        card.add(emailBtn);
        card.add(Box.createVerticalStrut(8));
        card.add(playAgain);
        card.add(Box.createVerticalStrut(8));
        card.add(exit);

        outer.add(card);
        return outer;
    }

    // ------------------------------------------------------------------
    // Game flow
    // ------------------------------------------------------------------
    private static void startQuiz() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                    "Please enter your name first - it goes on your certificate.",
                    "Name required", JOptionPane.WARNING_MESSAGE);
            nameField.requestFocusInWindow();
            return;
        }
        studentName = name;
        String email = emailField.getText().trim();
        if (email.isEmpty()) {
            studentEmail = ""; // no email is fine - the certificate can still be shown/downloaded
        } else if (!QuizStore.isValidEmail(email)) {
            JOptionPane.showMessageDialog(frame,
                    "That email address does not look right. Please fix it (or clear the field).",
                    "Check email", JOptionPane.WARNING_MESSAGE);
            emailField.requestFocusInWindow();
            return;
        } else {
            studentEmail = email;
        }
        secondsPerQuestion = QuizStore.loadSecondsPerQuestion(); // pick up instructor changes
        if (swingTimer != null && swingTimer.isRunning()) swingTimer.stop();
        Collections.shuffle(questions);
        score = 0;
        index = 0;
        timedOut = 0;
        millisUsed = 0;
        cards.show(mainPanel, "quiz");
        showQuestion();
    }

    private static void showQuestion() {
        if (index >= questions.size()) {
            showResults();
            return;
        }

        Question q = questions.get(index);
        answered = false;

        counterLabel.setText("Question " + (index + 1) + " of " + questions.size());
        questionLabel.setText(q.text);
        timeLeftLabel.setText(secondsPerQuestion + "s");
        timeLeftLabel.setForeground(MUTED);
        timerBar.setValue(100);

        buttonsPanel.removeAll();
        for (int j = 0; j < q.options.length; j++) {
            final int choice = j + 1;
            buttonsPanel.add(makeAnswerButton(q.options[j], true, false, false,
                    () -> answerClicked(choice)));
        }
        buttonsPanel.revalidate();
        buttonsPanel.repaint();

        questionStartTime = System.currentTimeMillis();
        deadline = questionStartTime + secondsPerQuestion * 1000L;

        if (swingTimer != null) swingTimer.stop();
        swingTimer = new javax.swing.Timer(100, e -> tick());
        swingTimer.start();
    }

    private static void tick() {
        long left = deadline - System.currentTimeMillis();
        if (left <= 0) {
            swingTimer.stop();
            answered = true;
            millisUsed += secondsPerQuestion * 1000L;
            timedOut++;
            reveal(-1);
            return;
        }
        int total = secondsPerQuestion * 1000;
        int pct = (int) Math.round(100.0 * left / total);
        timerBar.setValue(pct);
        timerBar.setForeground(pct <= 25 ? RED : (pct <= 50 ? new Color(255, 165, 0) : ACCENT));

        int secs = (int) Math.ceil(left / 1000.0);
        timeLeftLabel.setText(secs + "s");
        timeLeftLabel.setForeground(secs <= 5 ? RED : MUTED);
    }

    private static void answerClicked(int choice) {
        if (answered) return;
        long now = System.currentTimeMillis();
        if (now >= deadline) return; // too late; tick() will handle the timeout

        swingTimer.stop();
        answered = true;
        millisUsed += now - questionStartTime;
        reveal(choice);
    }

    private static JButton makeAnswerButton(String text, boolean enabled, boolean correct,
                                            boolean wrongPick, Runnable action) {
        Color base, fg;
        if (correct)      { base = GREEN; fg = Color.WHITE; }
        else if (wrongPick){ base = RED;   fg = Color.WHITE; }
        else if (enabled)  { base = CARD;  fg = TEXT; }
        else               { base = CARD;  fg = MUTED; }

        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                if (enabled && getModel().isRollover()) {
                    g2.setColor(isCorrect ? GREEN : UiTheme.ACCENT_DK);
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 14, 14);
                    g2.setStroke(new BasicStroke(2.4f));
                }
                g2.dispose();
                super.paintComponent(g);
            }

            private final boolean isCorrect = correct;
        };
        b.setFont(UiTheme.bodyMedium());
        b.setEnabled(false); // custom paint; we control look via colors above
        b.setBackground(base);
        b.setForeground(fg);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (action != null) {
            b.addActionListener(e -> action.run());
        }
        return b;
    }

    /** After an answer (or timeout): lock buttons, color the correct one, penalize wrong. */
    private static void reveal(int choice) {
        Question q = questions.get(index);
        buttonsPanel.removeAll();

        for (int j = 0; j < q.options.length; j++) {
            boolean isCorrect = (j == q.correctIndex);
            boolean isWrongPick = (choice - 1 == j && !isCorrect);
            buttonsPanel.add(makeAnswerButton(q.options[j], false, isCorrect, isWrongPick, null));
        }
        buttonsPanel.revalidate();
        buttonsPanel.repaint();

        if (choice - 1 == q.correctIndex) score++;

        javax.swing.Timer pause = new javax.swing.Timer(1300, e -> {
            index++;
            showQuestion();
        });
        pause.setRepeats(false);
        pause.start();
    }

    /** Emails the already-generated certificate in the background (optional convenience). */
    private static void emailCertificate() {
        if (!QuizStore.isEmailConfigured()) {
            offerEmailSetup();
            return;
        }
        if (studentEmail.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                    "You did not enter an email address on the start screen.\n"
                    + "You can still use \"View / Download my Certificate\" without one.",
                    "No email address", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!QuizStore.isValidEmail(studentEmail)) {
            JOptionPane.showMessageDialog(frame, "The email address is not valid.",
                    "Check email", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final java.io.File cert;
        if (lastCertFile != null && lastCertFile.exists()) {
            cert = lastCertFile;
        } else {
            generateCertificate();
            cert = lastCertFile;
        }
        if (cert == null) {
            JOptionPane.showMessageDialog(frame, "Could not create the certificate file.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final String to = studentEmail;
        emailBtnRef.setEnabled(false);
        emailBtnRef.setText("Sending...");

        new Thread(() -> {
            EmailSender.SendResult r = EmailSender.sendCertificate(
                    QuizStore.loadEmailSettings(), to, studentName,
                    score, questions.size(),
                    QuizStore.gradeFor(score * 100 / Math.max(1, questions.size())), cert);
            QuizStore.logEmail(r.ok ? "SENT" : "FAILED", to, studentName,
                    score, questions.size(), r.message);
            SwingUtilities.invokeLater(() -> {
                emailBtnRef.setEnabled(true);
                emailBtnRef.setText("Email my Certificate");
                JOptionPane.showMessageDialog(frame, r.message,
                        r.ok ? "Sent" : "Send failed",
                        r.ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
            });
        }, "email-certificate").start();
    }

    /**
     * No mail server configured yet. Instead of a dead end, walk the instructor
     * through setup right now: password, then the Email tab settings inline.
     */
    private static void offerEmailSetup() {
        JPasswordField pf = new JPasswordField(20);
        JPanel setup = new JPanel(new BorderLayout(0, 8));
        JLabel info = new JLabel("<html>Certificates are delivered by email, so the mail server<br>"
                + "must be set up once. Enter the instructor password<br>to configure it now:</html>");
        setup.add(info, BorderLayout.NORTH);
        setup.add(pf, BorderLayout.CENTER);
        int ok = JOptionPane.showConfirmDialog(frame, setup,
                "One-time email setup", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        if (!QuizStore.INSTRUCTOR_PASSWORD.equals(new String(pf.getPassword()))) {
            JOptionPane.showMessageDialog(frame,
                    "Wrong password. A student cannot configure email - ask your instructor.",
                    "Access denied", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Reuse the instructor panel's Email tab (modal dialog over the main window)
        InstructorPanel.showPanel(frame);
        // If the instructor saved a host while in there, continue with delivery:
        if (QuizStore.isEmailConfigured()) {
            emailCertificate();
        }
    }

    private static void showResults() {
        if (swingTimer != null) swingTimer.stop();
        int total = questions.size();
        int pct = total == 0 ? 0 : score * 100 / total;
        String grade = QuizStore.gradeFor(pct);

        // record the attempt so the instructor can see it
        QuizStore.saveResult(studentName, studentEmail, score, total, pct, grade,
                (int) (millisUsed / 1000), "GUI");

        bigResult.setText(score + " / " + total);
        String detail = "You answered " + score
                + (score == 1 ? " question" : " questions") + " correctly";
        if (timedOut > 0) detail += "  |  " + timedOut + " timed out";
        detail += "  |  " + (millisUsed / 1000) + "s total";
        detailResult.setText(detail);
        feedbackLabel.setText(getFeedback(pct));

        // Generate the certificate immediately so the student can view/download it
        // without needing email at all.
        generateCertificate();

        cards.show(mainPanel, "results");
    }

    /** Creates the certificate PNG on disk right away; called when the quiz ends. */
    private static void generateCertificate() {
        int total = questions.size();
        int pct = total == 0 ? 0 : score * 100 / total;
        lastCertFile = CertificateMaker.makeCertificate(studentName, score, total,
                pct, QuizStore.gradeFor(pct), QuizStore.today());
    }

    /** Opens the finished certificate in the system's default image viewer. */
    private static void showCertificate() {
        if (lastCertFile == null || !lastCertFile.exists()) {
            generateCertificate();
        }
        if (lastCertFile == null || !lastCertFile.exists()) {
            JOptionPane.showMessageDialog(frame, "Could not create or find the certificate file.",
                    "Certificate", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            java.awt.Desktop.getDesktop().open(lastCertFile.getAbsoluteFile());
        } catch (Exception ex) {
            // No desktop integration (e.g. server or minimal Linux) - tell the student where it is.
            JOptionPane.showMessageDialog(frame,
                "Certificate saved to:\n" + lastCertFile.getAbsolutePath()
                + "\n\n(Open that file with any image viewer.)",
                "Certificate saved", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static String getFeedback(int pct) {
        if (pct == 100) return "Perfect score! Excellent work.";
        if (pct >= 70) return "Great job!";
        if (pct >= 40) return "Not bad - a bit more practice and you'll ace it.";
        return "Keep practicing - you'll get there.";
    }

    // ------------------------------------------------------------------
    // CSV loading (same format as the console version)
    // ------------------------------------------------------------------
    private static List<Question> loadQuestionsFromCsv(String filePath) {
        List<Question> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) continue;

                String[] parts = splitCsvLine(line);
                if (parts.length != 6) {
                    System.out.println("Skipping malformed line " + lineNumber
                            + ": expected 6 fields, found " + parts.length);
                    continue;
                }
                try {
                    String text = parts[0].trim();
                    String[] options = new String[]{
                            parts[1].trim(), parts[2].trim(), parts[3].trim(), parts[4].trim()
                    };
                    int correctNumber = Integer.parseInt(parts[5].trim());
                    if (correctNumber >= 1 && correctNumber <= 4) {
                        list.add(new Question(text, options, correctNumber - 1));
                    } else {
                        System.out.println("Skipping line " + lineNumber
                                + ": correct-answer number must be 1-4.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Skipping line " + lineNumber
                            + ": correct-answer field is not a number.");
                }
            }
        } catch (IOException e) {
            System.out.println("Could not read file '" + filePath + "': " + e.getMessage());
        }
        return list;
    }

    /** Splits a CSV line on commas, honoring double-quoted fields that may contain commas. */
    private static String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
