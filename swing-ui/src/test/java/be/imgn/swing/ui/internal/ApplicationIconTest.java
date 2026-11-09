package be.imgn.swing.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;

/** The main frame's icon image, made from the application's icon. */
class ApplicationIconTest {

    @Test
    void takesTheImageOfAnImageIcon() {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);

        assertThat(ApplicationIcon.image(new ImageIcon(image))).isSameAs(image);
    }

    @Test
    void makesNoImageOfAnIconOtherThanAnImageIconWithoutWidthOrHeight() {
        assertThat(ApplicationIcon.image(new SizedIcon(0, 2))).isNull();
        assertThat(ApplicationIcon.image(new SizedIcon(3, 0))).isNull();
    }

    @Test
    void paintsAnyOtherIconIntoATransparentImageOfItsSize() {
        Icon icon = new Icon() {
            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                graphics.setColor(Color.RED);
                graphics.fillRect(x, y, 1, 1);
            }

            @Override
            public int getIconWidth() {
                return 3;
            }

            @Override
            public int getIconHeight() {
                return 2;
            }
        };

        Image image = Objects.requireNonNull(ApplicationIcon.image(icon));

        assertThat(image).isInstanceOf(BufferedImage.class);
        BufferedImage painted = (BufferedImage) image;
        assertThat(painted.getType()).isEqualTo(BufferedImage.TYPE_INT_ARGB);
        assertThat(painted.getWidth()).isEqualTo(3);
        assertThat(painted.getHeight()).isEqualTo(2);
        assertThat(painted.getRGB(0, 0)).isEqualTo(Color.RED.getRGB());
        assertThat(painted.getRGB(2, 1) >>> 24).isZero();
    }

    private record SizedIcon(int width, int height) implements Icon {
        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {}

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }
}
