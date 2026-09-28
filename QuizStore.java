import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/**
 * Shared storage for the quiz app: settings (time per question), student
 * results, and small CSV/file helpers. Everything is plain files in the
 * app folder, so nothing needs installing.
 */
public class QuizStore {

    public static final String SETTINGS_FILE = "settings.txt";
    public static final String RESULTS_FILE = "results.csv";
    public static final String CERTS_DIR = "certificates";
    public static final String EMAIL_FILE = "email.properties";

    /** Instructor password - change to whatever you like. */
    public static final String INSTRUCTOR_PASSWORD = "teach123";

    /** Load time per question from settings.txt; default 20 seconds. */
    public static int loadSecondsPerQuestion() {
        File f = new File(SETTINGS_FILE);
        if (f.exists()) {
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String line = r.readLine();
                if (line != null) {
                    int v = Integer.parseInt(line.trim());
                    if (v >= 1 && v <= 3600) return v;
                }
            } catch (Exception ignored) {
            }
        }
        return 20;
    }

    /** Save time per question to settings.txt. */
    public static void saveSecondsPerQuestion(int seconds) {
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(SETTINGS_FILE), StandardCharsets.UTF_8))) {
            w.println(seconds);
        } catch (IOException ignored) {
        }
    }

    /** Append a finished attempt to results.csv.
     *  Columns: name,email,score,total,percent,grade,timeSec,date,time,mode */
    public static void saveResult(String studentName, String studentEmail, int score, int total,
                                  int percent, String grade, int timeSec, String mode) {
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(RESULTS_FILE, true), StandardCharsets.UTF_8))) {
            w.println(String.join(",",
                    csvEscape(studentName),
                    csvEscape(studentEmail == null ? "" : studentEmail),
                    csvEscape(String.valueOf(score)),
                    csvEscape(String.valueOf(total)),
                    csvEscape(String.valueOf(percent)),
                    csvEscape(grade),
                    csvEscape(String.valueOf(timeSec)),
                    csvEscape(today()),
                    csvEscape(nowTime()),
                    csvEscape(mode)));
        } catch (IOException ignored) {
        }
    }

    /** All saved results from results.csv, oldest first.
     *  Old 9-column rows (before the email column existed) are padded so the
     *  email column is always present at index 1. */
    public static List<String[]> loadResults() {
        List<String[]> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(RESULTS_FILE), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = splitCsvLine(line);
                if (p.length == 9) { // legacy row without email: pad after name
                    String[] fixed = new String[10];
                    fixed[0] = p[0];
                    fixed[1] = "";
                    System.arraycopy(p, 1, fixed, 2, 8);
                    p = fixed;
                }
                if (p.length >= 10) out.add(p);
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Email log: one row per certificate delivery attempt
    // Columns: status,to,name,score,total,date,time,detail
    // ------------------------------------------------------------------

    public static final String EMAIL_LOG_FILE = "email_log.csv";

    public static void logEmail(String status, String to, String studentName,
                                int score, int total, String detail) {
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(EMAIL_LOG_FILE, true), StandardCharsets.UTF_8))) {
            w.println(String.join(",",
                    csvEscape(status),
                    csvEscape(to == null ? "" : to),
                    csvEscape(studentName == null ? "" : studentName),
                    csvEscape(String.valueOf(score)),
                    csvEscape(String.valueOf(total)),
                    csvEscape(today()),
                    csvEscape(nowTime()),
                    csvEscape(detail == null ? "" : detail)));
        } catch (IOException ignored) {
        }
    }

    /** All email log rows, oldest first. */
    public static List<String[]> loadEmailLog() {
        List<String[]> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(EMAIL_LOG_FILE), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = splitCsvLine(line);
                if (p.length >= 8) out.add(p);
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    /** Letter grade for a percentage (A-F scale). */
    public static String gradeFor(int percent) {
        if (percent >= 90) return "A";
        if (percent >= 80) return "B";
        if (percent >= 70) return "C";
        if (percent >= 60) return "D";
        if (percent >= 50) return "E";
        return "F";
    }

    public static String today() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    public static String nowTime() {
        return new java.text.SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    // ------------------------------------------------------------------
    // Email (SMTP) settings, stored in email.properties
    // ------------------------------------------------------------------

    public static Properties loadEmailSettings() {
        Properties p = new Properties();
        File f = new File(EMAIL_FILE);
        if (f.exists()) {
            try (FileInputStream in = new FileInputStream(f)) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        }
        return p;
    }

    public static void saveEmailSettings(String host, String port, boolean ssl,
                                         String user, String pass, String from) {
        Properties p = new Properties();
        p.setProperty("smtp.host", host == null ? "" : host.trim());
        p.setProperty("smtp.port", port == null ? "" : port.trim());
        p.setProperty("smtp.ssl", String.valueOf(ssl));
        p.setProperty("smtp.user", user == null ? "" : user.trim());
        p.setProperty("smtp.pass", pass == null ? "" : pass);
        p.setProperty("smtp.from", from == null ? "" : from.trim());
        try (Writer w = new OutputStreamWriter(new FileOutputStream(EMAIL_FILE),
                StandardCharsets.UTF_8)) {
            p.store(w, "Email settings for sending student certificates");
        } catch (IOException ignored) {
        }
    }

    /** True when the instructor has set up a mail server (email.properties has a host). */
    public static boolean isEmailConfigured() {
        return !loadEmailSettings().getProperty("smtp.host", "").trim().isEmpty();
    }

    /** Basic sanity check for an email address. */
    public static boolean isValidEmail(String s) {
        if (s == null) return false;
        String t = s.trim();
        int at = t.indexOf('@');
        if (at < 1 || at == t.length() - 1 || t.contains(" ")) return false;
        int dot = t.lastIndexOf('.');
        return dot > at + 1 && dot < t.length() - 1;
    }

    /** Escape a field for CSV output (quotes when needed). */
    public static String csvEscape(String s) {
        String r = s.replace("\"", "\"\"");
        if (r.contains(",") || r.contains("\"") || r.contains("\n") || r.contains("\r")) {
            r = "\"" + r + "\"";
        }
        return r;
    }

    /** Split a CSV line on commas, honoring double-quoted fields. */
    public static String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }
}
