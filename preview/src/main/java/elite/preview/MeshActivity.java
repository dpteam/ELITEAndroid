package elite.preview;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
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
    private LinearLayout throttlePanel;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        if (savedInstanceState != null) mesh.restoreTestState(savedInstanceState.getBundle("mesh-test"), models.length);
        mesh.statusListener = status -> {
            if (renderState != null) renderState.setText(status);
        };
        root.addView(mesh);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(6, 2, 6, 2);
        panel.setBackgroundColor(0xb0000000);
        title = new TextView(this);
        title.setTextSize(12);
        title.setTextColor(0xffffffff);
        panel.addView(title);
        TextView notice = new TextView(this);
        notice.setTextSize(10);
        notice.setText("TEST, not full game · Drag to rotate / hold to steer");
        notice.setTextColor(0xffffd86b);
        panel.addView(notice);
        renderState = new TextView(this);
        renderState.setTextSize(10);
        renderState.setMaxLines(2);
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
        button(controls, "Session", v -> startActivity(new Intent(this, SessionActivity.class)));
        HorizontalScrollView controlScroll = new HorizontalScrollView(this);
        controlScroll.addView(controls);
        panel.addView(controlScroll);
        throttlePanel = new LinearLayout(this);
        throttlePanel.setOrientation(LinearLayout.VERTICAL);
        throttlePanel.setVisibility(View.GONE);
        TextView throttleLabel = new TextView(this);
        throttleLabel.setTextSize(10);
        throttleLabel.setText("Flight speed target: 1…36 · no collisions");
        throttleLabel.setTextColor(0xffb0d8ff);
        throttlePanel.addView(throttleLabel);
        SeekBar throttle = new SeekBar(this);
        throttle.setMax(35);
        throttle.setProgress(mesh.throttleTarget - 1);
        throttle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) { mesh.throttleTarget = value + 1; }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        throttlePanel.addView(throttle);
        panel.addView(throttlePanel);
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(panel, layout);
        setContentView(root);
        // Coriolis has substantial geometry and is a useful default visual check.
        if (savedInstanceState == null) {
            for (int i = 0; i < models.length; ++i) if (models[i].equals("ship0")) selected = i;
            mesh.model = selected;
        } else selected = mesh.model;
        mode = mesh.flags == 0 ? 0 : mesh.flags == 1 ? 1 : 2;
        updateTitle();
    }

    private void button(LinearLayout row, String text, View.OnClickListener action) {
        Button button = new Button(this);
        button.setTextSize(10);
        button.setAllCaps(false);
        button.setMinimumWidth(0); button.setMinWidth(0);
        button.setMinimumHeight(0); button.setMinHeight(0);
        button.setPadding(10, 0, 10, 0);
        button.setText(text);
        button.setOnClickListener(action);
        row.addView(button, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, (int)(36 * getResources().getDisplayMetrics().density)));
    }
    private void select(int delta) {
        selected = (selected + delta + models.length) % models.length;
        mesh.model = selected;
        updateTitle();
    }
    private void updateTitle() {
        if (throttlePanel != null) throttlePanel.setVisibility(mesh.flightMode ? View.VISIBLE : View.GONE);
        title.setText((selected + 1) + "/" + models.length + "  " + models[selected]
            + " · " + (mesh != null && mesh.flightMode ? "Flight test" : new String[]{"Wireframe", "Solid", "Shaded"}[mode]));
    }
    @Override public void onSaveInstanceState(Bundle out) {
        if (mesh != null) out.putBundle("mesh-test", mesh.saveTestState());
        super.onSaveInstanceState(out);
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
