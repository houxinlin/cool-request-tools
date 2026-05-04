package dev.coolrequest.tool.wallhaven;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WallpaperApplier {

    private static final Logger LOG = Logger.getInstance(WallpaperApplier.class);

    public enum Target {
        EDITOR("idea.background.editor", "编辑器背景"),
        FRAME("idea.background.frame", "IDE 整体背景"),
        BOTH("", "全部（编辑器 + IDE）");

        public final String propKey;
        public final String label;

        Target(String propKey, String label) {
            this.propKey = propKey;
            this.label = label;
        }

        @Override
        public String toString() { return label; }
    }

    public enum Fill {
        SCALE("缩放铺满"), TILE("平铺"), CENTER("居中"), PLAIN("原始");
        public final String label;
        Fill(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum Anchor {
        CENTER("居中"), TOP_CENTER("顶部居中"), BOTTOM_CENTER("底部居中"),
        MIDDLE_LEFT("左中"), MIDDLE_RIGHT("右中"),
        TOP_LEFT("左上"), TOP_RIGHT("右上"),
        BOTTOM_LEFT("左下"), BOTTOM_RIGHT("右下");
        public final String label;
        Anchor(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public static Path saveImage(Wallpaper w, byte[] bytes) throws IOException {
        Path dir = Path.of(System.getProperty("user.home"),
                ".config", ".cool-request", "tools-data", "wallpaper");
        Files.createDirectories(dir);
        Path file = dir.resolve("wallpaper-" + w.id + "." + w.fileExtension());
        Files.write(file, bytes);
        return file;
    }

    public static void apply(Path imageFile, Target target, int opacity, Fill fill, Anchor anchor) {
        String value = imageFile.toAbsolutePath().toString().trim()
                + "," + opacity
                + "," + fill.name().toLowerCase()
                + "," + anchor.name().toLowerCase();
        PropertiesComponent pc = PropertiesComponent.getInstance();
        if (target == Target.BOTH) {
            pc.setValue("idea.background.editor", value);
            pc.setValue("idea.background.frame", value);
        } else {
            pc.setValue(target.propKey, value);
        }
        forceRefresh();
    }

    public static void clear(Target target) {
        PropertiesComponent pc = PropertiesComponent.getInstance();
        if (target == Target.BOTH) {
            pc.unsetValue(Target.EDITOR.propKey);
            pc.unsetValue(Target.FRAME.propKey);
        } else {
            pc.unsetValue(target.propKey);
        }
        forceRefresh();
    }

    private static void forceRefresh() {
        Runnable r = () -> {
            IdeBackgroundUtil.repaintAllWindows();
            for (Frame f : Frame.getFrames()) {
                if (f != null && f.isShowing()) {
                    f.invalidate();
                    f.validate();
                    f.repaint();
                }
            }
            for (Window w : Window.getWindows()) {
                if (w != null && w.isShowing()) w.repaint();
            }
            JFrame any = WindowManager.getInstance().findVisibleFrame();
            if (any != null) any.repaint();
        };
        ApplicationManager.getApplication().invokeLater(r, ModalityState.any());
    }
}
