package elite.app;

import android.app.Activity;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;

/* loaded from: classes.dex */
public class EliteActivity extends Activity {
    EliteView m_eliteView;
    SensorManager m_sensorManager;

    @Override // android.app.Activity
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.m_sensorManager = (SensorManager) getSystemService("sensor");
        requestWindowFeature(1);
        setContentView(R.layout.main);
        this.m_eliteView = (EliteView) findViewById(R.id.elite_view);
        this.m_eliteView.setActivity(this);
        registerListeners();
        setVolumeControlStream(3);
    }

    private void registerListeners() {
        if (this.m_sensorManager == null || this.m_eliteView == null) {
            return;
        }
        android.hardware.Sensor magnetic = this.m_sensorManager.getDefaultSensor(2);
        android.hardware.Sensor accelerometer = this.m_sensorManager.getDefaultSensor(1);
        if (magnetic != null) {
            this.m_sensorManager.registerListener(this.m_eliteView, magnetic, 1);
        }
        if (accelerometer != null) {
            this.m_sensorManager.registerListener(this.m_eliteView, accelerometer, 1);
        }
    }

    private void unregisterListeners() {
        if (this.m_sensorManager != null && this.m_eliteView != null) {
            this.m_sensorManager.unregisterListener(this.m_eliteView);
        }
    }

    @Override // android.app.Activity
    public void onDestroy() {
        Log.e("EliteActivity", "Destroyed, unregisterListener");
        unregisterListeners();
        if (this.m_eliteView != null) {
            this.m_eliteView.releaseMediaPlayer();
        }
        super.onDestroy();
    }

    @Override // android.app.Activity
    public void onPause() throws IllegalStateException {
        Log.e("EliteActivity", "Paused, unregisterListener");
        unregisterListeners();
        super.onPause();
        this.m_eliteView.onPause();
    }

    @Override // android.app.Activity
    public void onResume() throws IllegalStateException {
        Log.e("EliteActivity", "Resumed, registerListener");
        registerListeners();
        super.onResume();
        this.m_eliteView.onResume();
    }
}
