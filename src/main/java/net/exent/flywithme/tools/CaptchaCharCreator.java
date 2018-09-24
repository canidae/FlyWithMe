package net.exent.flywithme.tools;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * Colors:
 * -1: White
 * -2697515: Gray
 * -16777216: Black
 *
 * White never overwrites anything.
 * Black overwrites white.
 * Gray overwrites black.
 */

/**
 * This is a helper tool to create the captcha characters used in the NOAA captcha.
 */
public class CaptchaCharCreator {
    private static final int WHITE = -1;
    private static final int GRAY = -2697515;
    private static final int BLACK = -16777216;

    public static void main(String... args) throws Exception {
        BufferedImage output = null;
        for (String file : args) {
            System.out.println("Reading: " + file);
            BufferedImage img = ImageIO.read(new File(file));
            if (output == null) {
                output = img;
                continue;
            }
            if (img.getWidth() != output.getWidth() || img.getHeight() != output.getHeight()) {
                System.out.println(file + " isn't same size as the first input image, skipping file");
                continue;
            }
            for (int x = 0; x < img.getWidth(); ++x) {
                for (int y = 0; y < img.getHeight(); ++y) {
                    int rgb = img.getRGB(x, y);
                    if (rgb == GRAY) {
                        // Gray overwrites anything
                        output.setRGB(x, y, rgb);
                    } else if (rgb == BLACK) {
                        // black only overwrites white
                        if (output.getRGB(x, y) == WHITE)
                            output.setRGB(x, y, rgb);
                    }
                }
            }
        }
        if (output != null) {
            // replace gray with white
            for (int x = 0; x < output.getWidth(); ++x) {
                for (int y = 0; y < output.getHeight(); ++y) {
                    int rgb = output.getRGB(x, y);
                    if (rgb == GRAY)
                        output.setRGB(x, y, WHITE);
                }
            }
            ImageIO.write(output, "gif", new File("char.gif"));
        }
    }
}
