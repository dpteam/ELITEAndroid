package elite.preview;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.HorizontalScrollView;

/** Separate visual validation app. No menu/gameplay/save code is replaced. */
public final class MeshActivity extends Activity {
    private MeshView mesh;
    private TextView title;
    private TextView renderState;
    private String[] models;
    private int selected;
    private int mode = 1;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            System.loadLibrary("elite_preview");
            models = NativeMesh.modelNames();
            if (models.length == 0) throw new IllegalStateException("No imported ship models");
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            TextView message = new TextView(this);
            message.setText("Graphics test could not start:\n" + error);
            message.setPadding(32, 32, 32, 32);
            setContentView(message);
            return;
        }
        FrameLayout root = new FrameLayout(this);
        mesh = new MeshView(this);
        mesh.statusListener = status -> {
            if (renderState != null) renderState.setText(status);
        };
        root.addView(mesh);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(16, 8, 16, 8);
        panel.setBackgroundColor(0xb0000000);
        title = new TextView(this);
        title.setTextColor(0xffffffff);
        panel.addView(title);
        TextView notice = new TextView(this);
        notice.setText("TEST — NOT THE GAME · Drag: rotate / hold to steer in Flight · No collisions");
        notice.setTextColor(0xffffd86b);
        panel.addView(notice);
        renderState = new TextView(this);
        renderState.setTextColor(0xffb0d8ff);
        renderState.setText("GPU launch not yet observed on this device");
        panel.addView(renderState);
        LinearLayout controls = new LinearLayout(this);
        button(controls, "Previous", v -> select(-1));
        button(controls, "Next", v -> select(1));
        button(controls, "Mode", v -> {
            mode = (mode + 1) % 3;
            mesh.flags = mode == 0 ? 0 : mode == 1 ? 1 : 3;
            updateTitle();
        });
        button(controls, "Flight", v -> {
            mesh.flightMode = !mesh.flightMode;
            mesh.resetFlight();
            updateTitle();
        });
        button(controls, "Pause", v -> mesh.rotating = !mesh.rotating);
        button(controls, "Reset", v -> mesh.resetFlight());
        button(controls, "Station", v -> startActivity(new Intent(this, StationActivity.class)));
        HorizontalScrollView controlScroll = new HorizontalScrollView(this);
        controlScroll.addView(controls);
        panel.addView(controlScroll);
        TextView throttleLabel = new TextView(this);
        throttleLabel.setText("Flight throttle target (Adder profile): 1…36");
        throttleLabel.setTextColor(0xffb0d8ff);
        panel.addView(throttleLabel);
        SeekBar throttle = new SeekBar(this);
        throttle.setMax(35);
        throttle.setProgress(7);
        throttle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) { mesh.throttleTarget = value + 1; }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        panel.addView(throttle);
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(panel, layout);
        setContentView(root);
        // Coriolis has substantial geometry and is a useful default visual check.
        for (int i = 0; i < models.length; ++i) if (models[i].equals("ship0")) selected = i;
        mesh.model = selected;
        updateTitle();
    }

    private void button(LinearLayout row, String text, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(action);
        row.addView(button);
    }
    private void select(int delta) {
        selected = (selected + delta + models.length) % models.length;
        mesh.model = selected;
        updateTitle();
    }
    private void updateTitle() {
        title.setText((selected + 1) + "/" + models.length + "  " + models[selected]
            + " · " + (mesh != null && mesh.flightMode ? "Flight test" : new String[]{"Wireframe", "Solid", "Shaded"}[mode]));
    }
    @Override public void onResume() {
        super.onResume();
        if (mesh != null) { mesh.onResume(); mesh.clearFlightInput(); }
    }
    @Override public void onPause() {
        if (mesh != null) { mesh.clearFlightInput(); mesh.onPause(); }
        super.onPause();
    }
}
