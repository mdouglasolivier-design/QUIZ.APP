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

    // --- palette ---
    private static final Color BG = new Color(245, 247, 250);
    private static final Color CARD = Color.WHITE;
    private static final Color GREEN = new Color(40, 167, 69);
    private static final Color RED = new Color(220, 53, 69);
    private static final Color ACCENT = new Color(25, 118, 210);
    private static final Color TEXT = new Color(33, 37, 41);
    private static final Color MUTED = new Color(108, 117, 125);
    private static final Color BORDER = new Color(222, 226, 230);

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

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(30, 40, 30, 40)));
        card.setMaximumSize(new Dimension(460, 320));

        JLabel title = new JLabel("Java Quiz", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 34));
        title.setForeground(TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Enter your name and email - your certificate arrives by email");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitle.setForeground(MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        nameField = new JTextField(16);
        nameField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        nameField.setHorizontalAlignment(JTextField.CENTER);
        nameField.setMaximumSize(new Dimension(240, 34));
        nameField.setAlignmentX(Component.CENTER_ALIGNMENT);

        emailField = new JTextField(16);
        emailField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        emailField.setHorizontalAlignment(JTextField.CENTER);
        emailField.setMaximumSize(new Dimension(240, 34));
        emailField.setAlignmentX(Component.CENTER_ALIGNMENT);
        emailField.setToolTipText("Your certificate will be emailed to this address");

        emailLabel = new JLabel("Your email (your certificate is sent here)");
        emailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        emailLabel.setForeground(MUTED);
        emailLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel info = new JLabel(questions.size() + " questions  |  "
                + secondsPerQuestion + " seconds each");
        info.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        info.setForeground(MUTED);
        info.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton start = new JButton("Start Quiz");
        start.setFont(new Font("Segoe UI", Font.BOLD, 17));
        start.setBackground(ACCENT);
        start.setForeground(Color.WHITE);
        start.setFocusPainted(false);
        start.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        start.setAlignmentX(Component.CENTER_ALIGNMENT);
        start.addActionListener(e -> startQuiz());

        JButton consoleBtn = new JButton("Play in console instead");
        consoleBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        consoleBtn.setForeground(MUTED);
        consoleBtn.setFocusPainted(false);
        consoleBtn.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        consoleBtn.setContentAreaFilled(false);
        consoleBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        consoleBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        consoleBtn.addActionListener(e -> {
            frame.dispose();
            QuizApp.main(new String[0]);
        });

        JButton instructorBtn = new JButton("Instructor");
        instructorBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        instructorBtn.setForeground(MUTED);
        instructorBtn.setFocusPainted(false);
        instructorBtn.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        instructorBtn.setContentAreaFilled(false);
        instructorBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
        buttonsPanel.setMaximumSize(new Dimension(420, 400));

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

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(30, 40, 30, 40)));
        card.setMaximumSize(new Dimension(460, 340));

        JLabel done = new JLabel("Quiz Complete!");
        done.setFont(new Font("Segoe UI", Font.BOLD, 24));
        done.setForeground(TEXT);
        done.setAlignmentX(Component.CENTER_ALIGNMENT);

        bigResult = new JLabel("", SwingConstants.CENTER);
        bigResult.setFont(new Font("Segoe UI", Font.BOLD, 40));
        bigResult.setForeground(ACCENT);
        bigResult.setAlignmentX(Component.CENTER_ALIGNMENT);

        detailResult = new JLabel("", SwingConstants.CENTER);
        detailResult.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        detailResult.setForeground(MUTED);
        detailResult.setAlignmentX(Component.CENTER_ALIGNMENT);

        feedbackLabel = new JLabel("", SwingConstants.CENTER);
        feedbackLabel.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        feedbackLabel.setForeground(GREEN);
        feedbackLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton playAgain = new JButton("Play Again");
        playAgain.setFont(new Font("Segoe UI", Font.BOLD, 15));
        playAgain.setBackground(ACCENT);
        playAgain.setForeground(Color.WHITE);
        playAgain.setFocusPainted(false);
        playAgain.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        playAgain.setAlignmentX(Component.CENTER_ALIGNMENT);
        playAgain.addActionListener(e -> startQuiz());

        JButton exit = new JButton("Exit");
        exit.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        exit.setForeground(MUTED);
        exit.setFocusPainted(false);
        exit.setContentAreaFilled(false);
        exit.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        exit.setAlignmentX(Component.CENTER_ALIGNMENT);
        exit.addActionListener(e -> System.exit(0));

        JButton emailBtn = new JButton("Email my Certificate");
        emailBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        emailBtn.setForeground(Color.WHITE);
        emailBtn.setBackground(ACCENT);
        emailBtn.setOpaque(true);
        emailBtn.setBorderPainted(false);
        emailBtn.setFocusPainted(false);
        emailBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        emailBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        emailBtn.addActionListener(e -> emailCertificate());
        emailBtnRef = emailBtn;

        card.add(done);
        card.add(Box.createVerticalStrut(10));
        card.add(bigResult);
        card.add(Box.createVerticalStrut(8));
        card.add(detailResult);
        card.add(Box.createVerticalStrut(6));
        card.add(feedbackLabel);
        card.add(Box.createVerticalStrut(20));
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
            JOptionPane.showMessageDialog(frame,
                    "Please enter your email address - your certificate is sent to it.",
                    "Email required", JOptionPane.WARNING_MESSAGE);
            emailField.requestFocusInWindow();
            return;
        }
        if (!QuizStore.isValidEmail(email)) {
            JOptionPane.showMessageDialog(frame,
                    "That email address does not look right. Please fix it.",
                    "Check email", JOptionPane.WARNING_MESSAGE);
            emailField.requestFocusInWindow();
            return;
        }
        studentEmail = email;
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
            JButton b = new JButton(q.options[j]);
            b.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            b.setBackground(CARD);
            b.setForeground(TEXT);
            b.setFocusPainted(false);
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER, 1, true),
                    BorderFactory.createEmptyBorder(12, 14, 12, 14)));
            final int choice = j + 1;
            b.addActionListener(e -> answerClicked(choice));
            buttonsPanel.add(b);
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

    private static void reveal(int choice) {
        Question q = questions.get(index);
        buttonsPanel.removeAll();

        for (int j = 0; j < q.options.length; j++) {
            JButton b = new JButton(q.options[j]);
            b.setEnabled(false);
            b.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER, 1, true),
                    BorderFactory.createEmptyBorder(12, 14, 12, 14)));

            boolean isCorrect = (j == q.correctIndex);
            boolean isWrongPick = (choice - 1 == j && !isCorrect);

            if (isCorrect) {
                b.setBackground(GREEN);
                b.setForeground(Color.WHITE);
            } else if (isWrongPick) {
                b.setBackground(RED);
                b.setForeground(Color.WHITE);
            } else {
                b.setBackground(CARD);
                b.setForeground(MUTED);
            }
            buttonsPanel.add(b);
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

    /** Generates a fresh certificate and emails it to the student in the background. */
    private static void emailCertificate() {
        if (!QuizStore.isEmailConfigured()) {
            offerEmailSetup();
            return;
        }
        if (studentEmail.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                    "You did not enter an email address on the start screen.\n"
                    + "Type it there, play again (or use Play Again), then email it.",
                    "No email address", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!QuizStore.isValidEmail(studentEmail)) {
            JOptionPane.showMessageDialog(frame, "The email address is not valid.",
                    "Check email", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final java.io.File cert = CertificateMaker.makeCertificate(studentName, score,
                questions.size(), score * 100 / Math.max(1, questions.size()),
                QuizStore.gradeFor(score * 100 / Math.max(1, questions.size())),
                QuizStore.today());
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

        cards.show(mainPanel, "results");
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
