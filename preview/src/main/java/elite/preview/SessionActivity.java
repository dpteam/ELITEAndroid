package elite.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** New connected integration test. Return is a shortcut, NOT actual docking. */
public final class SessionActivity extends Activity {
    private PreviewSession session;
    private MeshView mesh;
    private boolean paused;
    private int throttle = 8, mode = 1;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            System.loadLibrary("elite_preview");
            String[] models = NativeMesh.modelNames();
            int model = -1;
            for (int i = 0; i < models.length; ++i) if (models[i].equals("ship0")) model = i;
            if (model < 0) throw new IllegalStateException("Missing station target model");
            byte[] saved = savedInstanceState == null ? null : savedInstanceState.getByteArray("session");
            session = new PreviewSession(saved, model);
            if (savedInstanceState != null) {
                paused = savedInstanceState.getBoolean("paused", false);
                throttle = Math.max(1, Math.min(36, savedInstanceState.getInt("throttle", 8)));
                mode = Math.max(0, Math.min(2, savedInstanceState.getInt("mode", 1)));
            }
            if (session.docked()) showStation(); else showFlight();
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            TextView message = new TextView(this);
            message.setText("Integration test could not start:\n" + error);
            setContentView(message);
        }
    }

    private void showStation() {
        setContentView(new StationPanel(this, new StationPanel.Host() {
            public byte[] state() { return session.station(); }
            public void trade(int item, boolean buy) { session.trade(item, buy); }
            public void reset() { session.reset(); paused = false; throttle = 8; }
            public void primary() { session.launch(); paused = false; showFlight(); }
            public String navigation() { return session.navigation(); }
            public void selectTarget(int delta) { session.shiftTarget(delta); }
            public void selectReachable() { session.selectReachable(); }
        }, "CONNECTED TEST · One commander · Not the full game",
            "Same-station return is a test shortcut, not docking. No disk saves.", "Launch test"));
    }

    private void showFlight() {
        FrameLayout root = new FrameLayout(this);
        MeshView view = new MeshView(this, session); mesh = view;
        view.rotating = !paused; view.throttleTarget = throttle;
        view.flags = mode == 0 ? 0 : mode == 1 ? 1 : 3;
        root.addView(view);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(6, 2, 6, 2); panel.setBackgroundColor(0xb0000000);
        panel.addView(label("CONNECTED TEST · Return = shortcut, NOT docking", 0xffffd86b));
        TextView summary = label(StationPanel.flightSummary(session.station()), 0xffffffff);
        panel.addView(summary);
        TextView status = label("Starting session flight", 0xffb0d8ff); status.setMaxLines(2);
        panel.addView(status);
        TextView dockingStatus = label(session.docking(), 0xffb0d8ff);
        panel.addView(dockingStatus);
        view.statusListener = text -> { if (mesh == view) status.setText(text); };
        LinearLayout actionRows = new LinearLayout(this);
        actionRows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout basicActions = new LinearLayout(this);
        Button pause = button(paused ? "Resume" : "Pause");
        pause.setOnClickListener(v -> { paused = !paused; view.rotating = !paused; pause.setText(paused ? "Resume" : "Pause"); });
        basicActions.addView(pause);
        Button reset = button("Reset scene"); reset.setOnClickListener(v -> view.resetFlight()); basicActions.addView(reset);
        Button render = button("Mode"); render.setOnClickListener(v -> {
            mode = (mode + 1) % 3; view.flags = mode == 0 ? 0 : mode == 1 ? 1 : 3;
        }); basicActions.addView(render);
        actionRows.addView(basicActions);
        LinearLayout transitionActions = new LinearLayout(this);
        Button back = button("Return (test)"); back.setOnClickListener(v -> returnToStation()); transitionActions.addView(back);
        Button jump = button("Jump (test)"); jump.setOnClickListener(v -> {
            try {
                session.jump();
                summary.setText(StationPanel.flightSummary(session.station()));
                dockingStatus.setText(session.docking());
            } catch (RuntimeException error) { status.setText("Jump rejected: " + error.getMessage()); }
        }); transitionActions.addView(jump);
        Button approach = button("Approach (test)"); approach.setOnClickListener(v -> {
            try { session.approach(120); dockingStatus.setText(session.docking()); }
            catch (RuntimeException error) { status.setText("Approach rejected: " + error.getMessage()); }
        }); transitionActions.addView(approach);
        Button dock = button("Dock (test)"); dock.setOnClickListener(v -> {
            try {
                if (mesh != null) { mesh.clearFlightInput(); mesh.onPause(); mesh = null; }
                session.dock(); showStation();
            }
            catch (RuntimeException error) { dockingStatus.setText("Dock rejected: " + error.getMessage()); }
        }); transitionActions.addView(dock);
        actionRows.addView(transitionActions);
        panel.addView(actionRows);
        panel.addView(label("Flight speed target (Adder): 1…36 · hold/drag to steer", 0xffb0d8ff));
        SeekBar speed = new SeekBar(this); speed.setMax(35); speed.setProgress(throttle - 1);
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) { throttle = value + 1; view.throttleTarget = throttle; }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        }); panel.addView(speed);
        root.addView(panel, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        setContentView(root);
    }

    private void returnToStation() {
        // Stop and join the GL work before changing phase. No stale flight tick
        // may race a station trade or appear after returning to the station.
        if (mesh != null) { mesh.clearFlightInput(); mesh.onPause(); mesh = null; }
        session.returnTest(); showStation();
    }

    private TextView label(String text, int colour) {
        TextView view = new TextView(this); view.setTextSize(10); view.setTextColor(colour); view.setText(text); return view;
    }
    private Button button(String text) {
        Button view = new Button(this); view.setText(text); view.setTextSize(10); view.setAllCaps(false);
        view.setMinimumWidth(0); view.setMinWidth(0); view.setMinimumHeight(0); view.setMinHeight(0);
        view.setPadding(10, 0, 10, 0);
        view.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
            (int)(36 * getResources().getDisplayMetrics().density)));
        return view;
    }
    @Override public void onSaveInstanceState(Bundle out) {
        if (session != null) out.putByteArray("session", session.snapshot());
        out.putBoolean("paused", paused); out.putInt("throttle", throttle); out.putInt("mode", mode);
        super.onSaveInstanceState(out);
    }
    @Override public void onResume() {
        super.onResume(); if (mesh != null) { mesh.onResume(); mesh.clearFlightInput(); }
    }
    @Override public void onPause() {
        if (mesh != null) { mesh.clearFlightInput(); mesh.onPause(); }
        super.onPause();
    }
    @Override public void onBackPressed() {
        if (session == null) { super.onBackPressed(); return; }
        if (!session.docked()) {
            new AlertDialog.Builder(this).setTitle("Return to station (test)?")
                .setMessage("This is a test shortcut, not a docking approach. Credits and cargo are retained.")
                .setNegativeButton("Cancel", null).setPositiveButton("Return", (dialog, which) -> returnToStation()).show();
        } else new AlertDialog.Builder(this).setTitle("Leave integration test?")
            .setMessage("This session has no disk save. Leaving discards this test commander.")
            .setNegativeButton("Stay", null).setPositiveButton("Leave", (dialog, which) -> finish()).show();
    }
}
