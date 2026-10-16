package elite.preview;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Standalone station diagnostic, separate from the connected Session screen. */
public final class StationActivity extends Activity {
    private byte[] state;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            System.loadLibrary("elite_preview");
            byte[] restored = savedInstanceState == null ? null : savedInstanceState.getByteArray("station-test-state");
            state = valid(restored) ? restored.clone() : NativeStation.reset();
            if (!valid(state)) throw new IllegalStateException("Invalid native station format");
            setContentView(new StationPanel(this, new StationPanel.Host() {
                public byte[] state() { return state; }
                public void trade(int item, boolean buy) { state = NativeStation.trade(state, item, buy); }
                public void reset() { state = NativeStation.reset(); }
                public void primary() { finish(); }
            }, "STATION TEST · No launch, missions or disk saves",
                "One unit per transaction. Flight test has independent state.", "Back to flight/model test"));
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            TextView message = new TextView(this);
            message.setText("Station core test could not start:\n" + error);
            setContentView(message);
        }
    }

    private static boolean valid(byte[] state) {
        if (state == null || state.length != 204) return false;
        ByteBuffer bytes = ByteBuffer.wrap(state).order(ByteOrder.LITTLE_ENDIAN);
        return bytes.getInt(0) == 0x4553544e && bytes.getInt(4) == 1;
    }
    @Override public void onSaveInstanceState(Bundle out) {
        if (valid(state)) out.putByteArray("station-test-state", state.clone());
        super.onSaveInstanceState(out);
    }
}
