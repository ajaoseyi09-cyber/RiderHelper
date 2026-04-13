package com.riderhelper;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

public class MainActivity extends AppCompatActivity {

    public static final String CHANNEL_ID = "rider_helper_channel";
    public static final String PREFS_NAME = "RiderHelperPrefs";
    public static final String PREF_SOUND = "sound_enabled";
    public static final String PREF_VIBRATE = "vibrate_enabled";
    public static final String PREF_AUTOTAP = "autotap_enabled";

    private TextView tvStatus;
    private TextView tvOrderCount;
    private TextView tvTapCount;
    private SwitchCompat switchSound, switchVibrate, switchAutoTap;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        tvStatus = findViewById(R.id.tvStatus);
        tvOrderCount = findViewById(R.id.tvOrderCount);
        tvTapCount = findViewById(R.id.tvTapCount);
        switchSound = findViewById(R.id.switchSound);
        switchVibrate = findViewById(R.id.switchVibrate);
        switchAutoTap = findViewById(R.id.switchAutoTap);

        // Load saved settings
        switchSound.setChecked(prefs.getBoolean(PREF_SOUND, true));
        switchVibrate.setChecked(prefs.getBoolean(PREF_VIBRATE, true));
        switchAutoTap.setChecked(prefs.getBoolean(PREF_AUTOTAP, true));

        // Save settings on change
        switchSound.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(PREF_SOUND, checked).apply());
        switchVibrate.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(PREF_VIBRATE, checked).apply());
        switchAutoTap.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(PREF_AUTOTAP, checked).apply());

        // Enable accessibility service button
        Button btnEnable = findViewById(R.id.btnEnableService);
        btnEnable.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        // Test alert button
        Button btnTest = findViewById(R.id.btnTestAlert);
        btnTest.setOnClickListener(v -> {
            AlertManager.triggerAlert(this, true, true);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        updateStats();
    }

    private void updateStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        if (enabled) {
            tvStatus.setText("🟢 ACTIVE — Watching for orders");
            tvStatus.setTextColor(0xFF00CC44);
        } else {
            tvStatus.setText("🔴 OFF — Tap button below to enable");
            tvStatus.setTextColor(0xFFFF4444);
        }
    }

    private void updateStats() {
        int orders = prefs.getInt("order_count", 0);
        int taps = prefs.getInt("tap_count", 0);
        tvOrderCount.setText(String.valueOf(orders));
        tvTapCount.setText(String.valueOf(taps));
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + OrderDetectorService.class.getCanonicalName();
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            if (splitter.next().equalsIgnoreCase(service)) return true;
        }
        return false;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Order Alerts",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Alerts when new Chowdeck orders appear");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 500, 200, 500});
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
