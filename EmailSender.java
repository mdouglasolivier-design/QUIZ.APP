import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.*;

/**
 * A tiny built-in SMTP client for sending emails with a PNG attachment.
 * Supports SSL (port 465) and STARTTLS (port 587/25), with AUTH LOGIN.
 * Uses only the JDK - no external mail libraries needed.
 */
public class EmailSender {

    /** The result of a send attempt. */
    public static class SendResult {
        public final boolean ok;
        public final String message;

        SendResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    /**
     * Send an email with a PNG certificate attached.
     *
     * @param props email settings from QuizStore.loadEmailSettings()
     * @param to    student email address
     * @param studentName display name used in the greeting
     * @param score, total, grade  quiz outcome, used in the message body
     * @param attachment the certificate PNG file
     */
    public static SendResult sendCertificate(Properties props, String to, String studentName,
                                             int score, int total, String grade, File attachment) {
        String host = props.getProperty("smtp.host", "").trim();
        String portStr = props.getProperty("smtp.port", "").trim();
        boolean useSsl = Boolean.parseBoolean(props.getProperty("smtp.ssl", "true"));
        String user = props.getProperty("smtp.user", "");
        String pass = props.getProperty("smtp.pass", "");
        String from = props.getProperty("smtp.from", "").trim();
        if (from.isEmpty()) from = user;

        if (host.isEmpty()) {
            return new SendResult(false, "No mail server configured. Ask the instructor to set it up (Instructor > Email).");
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            return new SendResult(false, "Invalid SMTP port: " + portStr);
        }

        String subject = attachment != null ? "Your Java Quiz Certificate" : "Quiz System - Test Email";
        String body = "Hello " + studentName + ",\n\n"
                + (attachment != null ? "Congratulations on completing the Java Quiz!\n\n" : "")
                + (attachment != null ? "Score: " + score + " / " + total + "\n" : "")
                + (attachment != null ? "Grade: " + grade + "\n\n" : "")
                + (attachment != null ? "Your certificate is attached as a PNG image.\n\n" : "This is a test email from the quiz system. Email sending works!\n\n")
                + "- Quiz System";

        try {
            sendMime(host, port, useSsl, user, pass, from, to, subject, body, attachment);
            return new SendResult(true, attachment != null
                    ? "Certificate emailed to " + to
                    : "Test email sent to " + to);
        } catch (Exception e) {
            return new SendResult(false, "Could not send: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Low-level SMTP with MIME attachment
    // ------------------------------------------------------------------

    private static void sendMime(String host, int port, boolean useSsl, String user, String pass,
                                 String from, String to, String subject, String body,
                                 File attachment) throws Exception {
        Socket socket = useSsl
                ? SSLSocketFactory.getDefault().createSocket(host, port)
                : new Socket(host, port);
        socket.setSoTimeout(20000);

        BufferedReader in = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.ISO_8859_1));
        Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1);

        String boundary = "----quizcert" + UUID.randomUUID().toString().replace("-", "");

        String greeting = in.readLine();
        if (greeting == null || !greeting.startsWith("220")) {
            throw new IOException("Bad server greeting: " + greeting);
        }

        String caps = smtpCmd(out, in, "EHLO localhost");

        if (!useSsl) {
            // Upgrade to TLS if the server offers STARTTLS
            if (caps != null && caps.toUpperCase(Locale.ROOT).contains("STARTTLS")) {
                smtpCmd(out, in, "STARTTLS");
                SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket tls = (SSLSocket) sf.createSocket(socket, host, port, true);
                tls.startHandshake();
                socket = tls;
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1);
                smtpCmd(out, in, "EHLO localhost");
            }
        }

        if (user != null && !user.isEmpty()) {
            String resp = smtpCmd(out, in, "AUTH LOGIN");
            if (resp.startsWith("334")) {
                smtpCmdRaw(out, in, Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)));
                smtpCmdRaw(out, in, Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8)));
            }
        }

        smtpCmd(out, in, "MAIL FROM:<" + from + ">");
        smtpCmd(out, in, "RCPT TO:<" + to + ">");
        smtpCmd(out, in, "DATA");

        StringBuilder data = new StringBuilder();
        data.append("From: ").append(from).append("\r\n");
        data.append("To: ").append(to).append("\r\n");
        data.append("Subject: ").append(subject).append("\r\n");
        data.append("MIME-Version: 1.0\r\n");
        data.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n");
        data.append("\r\n");

        // part 1: text body
        data.append("--").append(boundary).append("\r\n");
        data.append("Content-Type: text/plain; charset=UTF-8\r\n");
        data.append("\r\n");
        data.append(body.replace("\n", "\r\n"));
        data.append("\r\n");

        // part 2: the certificate PNG, base64 encoded (skipped for test emails)
        if (attachment != null) {
            byte[] png = java.nio.file.Files.readAllBytes(attachment.toPath());
            data.append("--").append(boundary).append("\r\n");
            data.append("Content-Type: image/png; name=\"").append(attachment.getName()).append("\"\r\n");
            data.append("Content-Transfer-Encoding: base64\r\n");
            data.append("Content-Disposition: attachment; filename=\"").append(attachment.getName()).append("\"\r\n");
            data.append("\r\n");
            String b64 = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.ISO_8859_1)).encodeToString(png);
            data.append(b64);
        }
        data.append("\r\n--").append(boundary).append("--\r\n");

        // dot-stuffing so lines starting with '.' are not mistaken for the terminator
        String payload = data.toString().replaceAll("(^|\\r\\n)\\.", "$1..");
        out.write(payload);
        out.write("\r\n.\r\n");
        out.flush();
        String dr = in.readLine();
        if (dr == null || !dr.startsWith("250")) {
            throw new IOException("Server rejected message: " + dr);
        }

        smtpCmd(out, in, "QUIT");
        socket.close();
    }

    /** Send a command and check the reply starts with the expected code. */
    private static String smtpCmd(Writer out, BufferedReader in, String cmd) throws IOException {
        out.write(cmd + "\r\n");
        out.flush();
        return readReply(in);
    }

    private static void smtpCmdRaw(Writer out, BufferedReader in, String arg) throws IOException {
        out.write(arg + "\r\n");
        out.flush();
        readReply(in);
    }

    /** Reads a (possibly multi-line) SMTP reply and returns it as one string. */
    private static String readReply(BufferedReader in) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line = in.readLine();
        if (line == null) throw new IOException("Connection closed by server");
        sb.append(line);
        while (line.length() >= 4 && line.charAt(3) == '-') { // 250-... continues
            line = in.readLine();
            if (line == null) throw new IOException("Connection closed by server");
            sb.append(" | ").append(line);
        }
        return sb.toString();
    }
}
