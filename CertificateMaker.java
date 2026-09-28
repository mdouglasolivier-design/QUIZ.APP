import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/**
 * Draws a completion certificate as a PNG image the student can keep or print.
 * No external libraries - uses the JDK's built-in 2D drawing.
 */
public class CertificateMaker {

    /** Create certificates/Certificate_Name_date.png and return the File (null on failure). */
    public static File makeCertificate(String studentName, int score, int total,
                                       int percent, String grade, String dateText) {
        File dir = new File(QuizStore.CERTS_DIR);
        if (!dir.exists()) dir.mkdirs();

        String safeName = studentName.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (safeName.isEmpty()) safeName = "Student";
        File out = new File(dir, "Certificate_" + safeName.replace(' ', '_') + "_" + dateText + ".png");

        int W = 1400, H = 990;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ---- background ----
        g.setColor(new Color(248, 246, 240));
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(25, 118, 210));
        g.fillRect(0, 0, W, 18);
        g.fillRect(0, H - 18, W, 18);

        // ---- border ----
        g.setColor(new Color(212, 175, 55));
        g.setStroke(new BasicStroke(6f));
        g.drawRoundRect(40, 40, W - 80, H - 80, 30, 30);
        g.setColor(new Color(222, 226, 230));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(58, 58, W - 116, H - 116, 20, 20);

        // ---- text ----
        g.setColor(new Color(33, 37, 41));
        g.setFont(new Font("Serif", Font.BOLD, 52));
        g.drawString("Certificate of Achievement", center("Certificate of Achievement", g, W), 170);

        g.setFont(new Font("Serif", Font.ITALIC, 26));
        String l1 = "This certificate is proudly presented to";
        g.drawString(l1, center(l1, g, W), 300);

        g.setColor(new Color(25, 118, 210));
        g.setFont(new Font("Serif", Font.BOLD, 72));
        g.drawString(studentName, center(studentName, g, W), 410);

        g.setColor(new Color(33, 37, 41));
        g.setFont(new Font("Serif", Font.ITALIC, 26));
        String l2 = "for successfully completing the Java Quiz";
        g.drawString(l2, center(l2, g, W), 500);

        g.setFont(new Font("Serif", Font.PLAIN, 30));
        String l3 = "Score: " + score + " / " + total + "   (" + percent + "%)";
        g.drawString(l3, center(l3, g, W), 590);

        String l4 = "Grade: " + grade;
        g.drawString(l4, center(l4, g, W), 650);

        g.setFont(new Font("Serif", Font.ITALIC, 22));
        g.setColor(new Color(108, 117, 125));
        String l5 = "Awarded on " + dateText;
        g.drawString(l5, center(l5, g, W), 760);

        // ---- signature line ----
        g.setColor(new Color(33, 37, 41));
        g.setFont(new Font("Serif", Font.PLAIN, 20));
        g.drawLine(W / 2 - 200, 880, W / 2 + 200, 880);
        String sig = "Instructor";
        g.drawString(sig, center(sig, g, W), 910);

        g.dispose();
        try {
            ImageIO.write(img, "png", out);
            return out;
        } catch (IOException e) {
            System.out.println("Could not save certificate: " + e.getMessage());
            return null;
        }
    }

    private static int center(String s, Graphics2D g, int width) {
        FontMetrics fm = g.getFontMetrics();
        return (width - fm.stringWidth(s)) / 2;
    }
}
