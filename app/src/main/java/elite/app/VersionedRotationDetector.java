package elite.app;

import android.os.Build;
import android.view.Display;

/* loaded from: classes.dex */
public abstract class VersionedRotationDetector {
    public Display m_display;

    public abstract int getRotation();

    private static class FroyoRotationDetector extends VersionedRotationDetector {
        private FroyoRotationDetector() {
        }

        @Override // elite.app.VersionedRotationDetector
        public int getRotation() {
            return this.m_display.getRotation();
        }
    }

    private static class EclairRotationDetector extends VersionedRotationDetector {
        private EclairRotationDetector() {
        }

        @Override // elite.app.VersionedRotationDetector
        public int getRotation() {
            return 1;
        }
    }

    public static VersionedRotationDetector newInstance(Display d) throws NumberFormatException {
        VersionedRotationDetector detector;
        int sdkVersion = Build.VERSION.SDK_INT;
        if (sdkVersion < 8) {
            detector = new EclairRotationDetector();
        } else {
            detector = new FroyoRotationDetector();
        }
        detector.m_display = d;
        return detector;
    }
}
