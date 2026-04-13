package com.riderhelper;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import androidx.core.app.NotificationCompat;

public class AlertManager {

    private static final int NOTIFICATION_ID = 1001;
    private static ToneGenerator toneGenerator;

    public static void triggerAlert(Context context, boolean sound, boolean vibrate) {
        if (sound) {
            playAlertSound();
        }
        if (vibrate) {
            vibratePhone(context);
        }
    }

    private static void playAlertSound() {
        try {
            // Use max volume alarm tone
            if (toneGenerator != null) {
                toneGenerator.release();
            }
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            // Play a series of urgent beeps
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500);
        } catch (Exception e) {
            try {
                // Fallback tone
                ToneGenerator fallback = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
                fallback.startTone(ToneGenerator.TONE_PROP_BEEP2, 1000);
            } catch (Exception ignored) {}
        }
    }

    private static void vibratePhone(Context context) {
        long[] pattern = {0, 300, 100, 300, 100, 300, 100, 600};

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    Vibrator v = vm.getDefaultVibrator();
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1));
                }
            } else {
                Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1));
                }
            }
        } catch (Exception e) {
            // Vibration unavailable
        }
    }

    public static void showNotification(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🔥 NEW ORDER AVAILABLE!")
                .setContentText("Tap to open Chowdeck Rider — auto-tap is working!")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 300, 100, 300})
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();

        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }
}
