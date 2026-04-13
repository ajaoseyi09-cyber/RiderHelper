package com.riderhelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Accessibility services restart automatically after reboot
            // This receiver just logs the boot for future features
        }
    }

    public static boolean isAccessibilityEnabled(Context context) {
        String service = context.getPackageName() + "/" + OrderDetectorService.class.getCanonicalName();
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
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
}
