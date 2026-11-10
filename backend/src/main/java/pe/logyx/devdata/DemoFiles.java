package pe.logyx.devdata;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.CubicCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Archivos generados para el seed dev: foto y firma (PNG) y una guía (PDF de una página). */
final class DemoFiles {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private DemoFiles() {}

    static byte[] photo(String caption) {
        BufferedImage img = new BufferedImage(640, 420, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(0x9CC3E6), 0, 260, new Color(0xE8F1F8)));
        g.fillRect(0, 0, 640, 260);
        g.setColor(new Color(0x8D8D8D));
        g.fillRect(0, 260, 640, 160);
        g.setColor(new Color(0xD9C7A3));                       // pallet de bolsas
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) g.fillRoundRect(170 + col * 60, 290 - row * 34, 56, 30, 8, 8);
        }
        g.setColor(new Color(0x1F3B57));
        g.fillRect(0, 370, 640, 50);
        g.setColor(Color.WHITE);
        try {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            g.drawString(caption, 20, 402);
        } catch (RuntimeException | Error e) {
            // sin fuentes del sistema (contenedores mínimos): la foto queda sin leyenda
        }
        g.dispose();
        return png(img);
    }

    static byte[] signature() {
        BufferedImage img = new BufferedImage(480, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 480, 180);
        g.setColor(new Color(0x1A237E));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new CubicCurve2D.Double(40, 120, 120, 10, 160, 170, 240, 80));
        g.draw(new CubicCurve2D.Double(240, 80, 300, 20, 330, 150, 430, 70));
        g.setColor(new Color(0x9E9E9E));
        g.drawLine(30, 150, 450, 150);
        g.dispose();
        return png(img);
    }

    /** PDF mínimo válido con una línea de texto (ASCII). */
    static byte[] pdf(String text) {
        String content = "BT /F1 18 Tf 72 720 Td (" + text.replace("(", "").replace(")", "") + ") Tj ET";
        String[] objects = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R "
                        + "/Resources << /Font << /F1 5 0 R >> >> >>",
                "<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"};
        StringBuilder sb = new StringBuilder("%PDF-1.4\n");
        int[] offsets = new int[objects.length];
        for (int i = 0; i < objects.length; i++) {
            offsets[i] = sb.length();
            sb.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
        }
        int xref = sb.length();
        sb.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
        for (int o : offsets) sb.append(String.format("%010d 00000 n \n", o));
        sb.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] png(BufferedImage img) {
        try (var out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
