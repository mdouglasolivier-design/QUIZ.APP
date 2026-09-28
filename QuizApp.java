import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class QuizApp {

    /** Default seconds per question (instructor can change it via settings.txt or the panel). */
    private static final int DEFAULT_SECONDS_PER_QUESTION = 20;

    /** All keyboard input is read by ONE background thread and queued here (race-free). */
    private static final BlockingQueue<InputLine> inputQueue = new LinkedBlockingQueue<>();
    private static volatile boolean inputClosed = false;

    /** A raw line of input, stamped with the moment Enter was pressed. */
    static class InputLine {
        final String text;
        final long arrivalMs;

        InputLine(String text, long arrivalMs) {
            this.text = text;
            this.arrivalMs = arrivalMs;
        }
    }

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

    static class AnswerResult {
        int answer = -1;
        boolean timeout;
        boolean quit;
    }

    public static void main(String[] args) {
        String filePath = args.length > 0 ? args[0] : "questions.csv";
        int secondsPerQuestion = QuizStore.loadSecondsPerQuestion();
        if (args.length > 1) {
            try {
                secondsPerQuestion = Integer.parseInt(args[1]);
                if (secondsPerQuestion < 1) secondsPerQuestion = DEFAULT_SECONDS_PER_QUESTION;
            } catch (NumberFormatException ignored) {
                System.out.println("Ignored invalid time limit '" + args[1] + "', using " + secondsPerQuestion + "s per question.");
            }
        }

        List<Question> questions = loadQuestionsFromCsv(filePath);

        if (questions.isEmpty()) {
            System.out.println("No questions were loaded. Check that '" + filePath + "' exists and is formatted correctly.");
            System.out.println("Each line must be: question,option1,option2,option3,option4,correctOptionNumber");
            return;
        }

        startInputReaderThread(); // must start before reading the name

        System.out.print("Enter your name: ");
        System.out.flush();
        String studentName = "Student";
        try {
            InputLine typed = inputQueue.poll(60, TimeUnit.SECONDS);
            if (typed != null && !typed.text.trim().isEmpty()) studentName = typed.text.trim();
        } catch (InterruptedException ignored) {
        }

        // Email is required: the certificate is delivered by email only.
        String studentEmail = "";
        while (studentEmail.isEmpty()) {
            System.out.print("Enter your email address (your certificate will be emailed here): ");
            System.out.flush();
            String typed = null;
            try {
                if (inputClosed && inputQueue.isEmpty()) { // stdin closed (Ctrl+D / pipe end)
                    System.out.println("(No email entered - certificate will be saved locally instead.)");
                    break;
                }
                InputLine line = inputQueue.poll(120, TimeUnit.SECONDS);
                if (line == null) break; // timed out waiting
                typed = line.text.trim();
            } catch (InterruptedException ignored) {
            }
            if (QuizStore.isValidEmail(typed)) {
                studentEmail = typed;
            } else {
                System.out.println("Please enter a valid email address, e.g. name@example.com");
            }
        }

        Collections.shuffle(questions); // randomize order each run

        Scanner scanner = new Scanner(System.in);
        int score = 0;
        long totalMsUsed = 0;
        int timedOut = 0;

        System.out.println("=== Welcome to the Java Quiz ===");
        System.out.println("Loaded " + questions.size() + " questions from " + filePath);
        System.out.println("You have " + secondsPerQuestion + " seconds per question.");
        System.out.println("Answer with 1, 2, 3 or 4 (type q to quit).\n");

        boolean quitEarly = false;
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            System.out.println("Q" + (i + 1) + ": " + q.text);
            for (int j = 0; j < q.options.length; j++) {
                System.out.println("  " + (j + 1) + ". " + q.options[j]);
            }

            long start = System.currentTimeMillis();
            AnswerResult result = getAnswerWithTimer(q.options.length, secondsPerQuestion);
            long elapsed = System.currentTimeMillis() - start;
            totalMsUsed += elapsed;

            if (result.quit) {
                quitEarly = true;
                break;
            }

            if (result.timeout) {
                timedOut++;
                System.out.println();
                System.out.println("TIME'S UP! The correct answer was: " + q.options[q.correctIndex]);
                System.out.println();
            } else if (result.answer - 1 == q.correctIndex) {
                System.out.println("Correct! (" + (elapsed / 1000.0) + "s)\n");
                score++;
            } else {
                System.out.println("Wrong. The correct answer was: " + q.options[q.correctIndex] + "\n");
            }
        }

        int total = questions.size();
        int pct = total == 0 ? 0 : score * 100 / total;
        String grade = QuizStore.gradeFor(pct);

        // record the attempt for the instructor
        QuizStore.saveResult(studentName, studentEmail, score, total, pct, grade,
                (int) (totalMsUsed / 1000), "Console");

        System.out.println("=== Quiz Complete ===");
        if (quitEarly) System.out.println("(You quit before finishing.)");
        System.out.println("Your score: " + score + "/" + total
                + (timedOut > 0 ? "  (" + timedOut + " timed out)" : ""));
        System.out.println("Total time: " + String.format("%.1f", totalMsUsed / 1000.0) + "s");
        System.out.println(getFeedback(score, total));

        // Certificate is always generated and delivered by email - no download step.
        java.io.File certFile = CertificateMaker.makeCertificate(studentName, score, total,
                pct, grade, QuizStore.today());

        if (!QuizStore.isEmailConfigured()) {
            System.out.println();
            System.out.println("[!] No mail server is configured yet, so the email cannot be sent now.");
            System.out.println("    Your certificate was saved here in the meantime:");
            System.out.println("    " + (certFile != null ? certFile.getAbsolutePath() : "(could not be created)"));
            System.out.println("    The instructor can set this up via Instructor > Email.");
        } else {
            System.out.println("Sending your certificate to " + studentEmail + " ...");
            EmailSender.SendResult r = EmailSender.sendCertificate(
                    QuizStore.loadEmailSettings(), studentEmail, studentName,
                    score, total, grade, certFile);
            QuizStore.logEmail(r.ok ? "SENT" : "FAILED", studentEmail, studentName,
                    score, total, r.message);
            System.out.println(r.message);
        }

        scanner.close();
    }

    /**
     * One daemon thread reads every line of keyboard input into a queue.
     * A single reader means no answer can ever be "swallowed" between questions.
     */
    private static void startInputReaderThread() {
        Thread reader = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            try {
                while (scanner.hasNextLine()) {
                    inputQueue.put(new InputLine(scanner.nextLine(), System.currentTimeMillis()));
                }
            } catch (NoSuchElementException | IllegalStateException ignored) {
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            inputClosed = true;
        });
        reader.setDaemon(true);
        reader.start();
    }

    /**
     * Waits for a valid answer until the deadline, then times out.
     * Returns timeout=true when time runs out, or quit=true when the user types q
     * (or input ends via Ctrl+D / Ctrl+Z).
     *
     * Note: nothing is printed while the player types, so their input can never
     * collide with the timer. The time limit is shown once, up front; the elapsed
     * time is reported after each answer instead.
     *
     * Input lines are stamped with the time Enter was pressed:
     *  - lines pressed before the buzzer always count (including type-ahead or pasted ones),
     *  - lines pressed after a buzzer are discarded, so a late answer can never
     *    leak into the next question.
     */
    private static AnswerResult getAnswerWithTimer(int numOptions, int seconds) {
        AnswerResult result = new AnswerResult();

        long deadline = System.currentTimeMillis() + seconds * 1000L;
        String prompt = "Your answer (1-" + numOptions + ") [" + seconds + "s]: ";
        System.out.print(prompt);
        System.out.flush();

        while (true) {
            if (inputClosed && inputQueue.isEmpty()) { // stdin closed (Ctrl+D / Ctrl+Z)
                result.quit = true;
                break;
            }
            long leftMs = deadline - System.currentTimeMillis();
            if (leftMs <= 0) {
                resolveTimeout(result, deadline, numOptions);
                break;
            }
            InputLine il;
            try {
                il = inputQueue.poll(Math.min(leftMs, 200), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                continue;
            }
            if (il == null) continue;
            if (il.arrivalMs > deadline) continue; // pressed after the buzzer: too late

            String line = il.text.trim();
            if (line.equalsIgnoreCase("q")) {
                result.quit = true;
                break;
            }
            try {
                int a = Integer.parseInt(line);
                if (a >= 1 && a <= numOptions) {
                    result.answer = a;
                    break;
                }
            } catch (NumberFormatException ignored) {
            }
            // Safe to print here: the player just pressed Enter, so nothing is mid-typing.
            System.out.print("Invalid choice. " + prompt);
            System.out.flush();
        }

        if (result.timeout || result.quit) System.out.println();
        return result;
    }

    /**
     * The buzzer just fired. Lines already queued with a pre-buzzer timestamp still
     * count (the player may have pressed Enter a few milliseconds before the deadline),
     * so salvage those first. Then briefly discard anything that arrives, so input
     * typed against the expired question is never counted for the next one.
     */
    private static void resolveTimeout(AnswerResult result, long deadline, int numOptions) {
        InputLine il;
        while ((il = inputQueue.poll()) != null) {
            if (il.arrivalMs > deadline) continue; // typed after the buzzer
            String t = il.text.trim();
            if (t.equalsIgnoreCase("q")) {
                result.quit = true;
                return;
            }
            try {
                int a = Integer.parseInt(t);
                if (a >= 1 && a <= numOptions) {
                    result.answer = a; // answered just in time
                    return;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        // Grace window: swallow input typed right after the buzzer for the dead question.
        long graceEnd = System.currentTimeMillis() + 400;
        try {
            while ((il = inputQueue.poll(Math.max(1, graceEnd - System.currentTimeMillis()), TimeUnit.MILLISECONDS)) != null) {
                // discard
            }
        } catch (InterruptedException ignored) {
        }
        result.timeout = true;
    }

    private static String getFeedback(int score, int total) {
        double pct = total == 0 ? 0 : (double) score / total * 100;
        if (pct == 100) return "Perfect score! Excellent work.";
        if (pct >= 70) return "Great job!";
        if (pct >= 40) return "Not bad - a bit more practice and you'll ace it.";
        return "Keep practicing - you'll get there.";
    }

    /**
     * Expects a CSV file where each line is:
     * question,option1,option2,option3,option4,correctOptionNumber(1-4)
     *
     * Fields may be wrapped in double quotes (needed if a field contains a comma).
     *
     * Example:
     * "What is the capital of Rwanda?",Nairobi,Kigali,Kampala,Bujumbura,2
     */
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
                    System.out.println("Skipping malformed line " + lineNumber + ": expected 6 fields, found " + parts.length);
                    continue;
                }

                String questionText = parts[0].trim();
                String[] options = new String[]{
                        parts[1].trim(), parts[2].trim(), parts[3].trim(), parts[4].trim()
                };

                int correctNumber;
                try {
                    correctNumber = Integer.parseInt(parts[5].trim());
                } catch (NumberFormatException e) {
                    System.out.println("Skipping line " + lineNumber + ": correct-answer field is not a number.");
                    continue;
                }

                if (correctNumber < 1 || correctNumber > 4) {
                    System.out.println("Skipping line " + lineNumber + ": correct-answer number must be 1-4.");
                    continue;
                }

                list.add(new Question(questionText, options, correctNumber - 1));
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
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { // escaped quote ""
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
