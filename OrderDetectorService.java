package com.riderhelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class OrderDetectorService extends AccessibilityService {

    // Keywords that appear when a new order comes in — adjust if needed
    private static final String[] ORDER_KEYWORDS = {
        "new order", "accept", "order request", "delivery request",
        "new delivery", "pickup", "tap to accept", "order available"
    };

    // Button texts to auto-tap
    private static final String[] ACCEPT_BUTTON_TEXTS = {
        "accept", "tap to accept", "grab", "take order", "accept order"
    };

    private boolean orderActive = false;
    private long lastOrderTime = 0;
    private static final long ORDER_COOLDOWN_MS = 5000; // 5 second cooldown
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        // Only process events from Chowdeck Rider
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !pkg.toString().contains("chowdeck")) return;

        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                checkForNewOrder(root);
                root.recycle();
            }
        }
    }

    private void checkForNewOrder(AccessibilityNodeInfo root) {
        long now = System.currentTimeMillis();

        // Cooldown to avoid spamming alerts for same order
        if (now - lastOrderTime < ORDER_COOLDOWN_MS) return;

        String screenText = extractAllText(root).toLowerCase();

        boolean orderFound = false;
        for (String keyword : ORDER_KEYWORDS) {
            if (screenText.contains(keyword)) {
                orderFound = true;
                break;
            }
        }

        if (orderFound) {
            lastOrderTime = now;
            onOrderDetected(root);
        }
    }

    private void onOrderDetected(AccessibilityNodeInfo root) {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);

        // Increment order count stat
        int count = prefs.getInt("order_count", 0);
        prefs.edit().putInt("order_count", count + 1).apply();

        boolean soundEnabled = prefs.getBoolean(MainActivity.PREF_SOUND, true);
        boolean vibrateEnabled = prefs.getBoolean(MainActivity.PREF_VIBRATE, true);
        boolean autoTapEnabled = prefs.getBoolean(MainActivity.PREF_AUTOTAP, true);

        // Fire alert immediately
        AlertManager.triggerAlert(this, soundEnabled, vibrateEnabled);
        AlertManager.showNotification(this);

        // Auto-tap — try immediately, then retry a few times
        if (autoTapEnabled) {
            tryAutoTap(root, prefs);
            // Retry taps in case screen updates
            handler.postDelayed(() -> {
                AccessibilityNodeInfo freshRoot = getRootInActiveWindow();
                if (freshRoot != null) {
                    tryAutoTap(freshRoot, prefs);
                    freshRoot.recycle();
                }
            }, 150);
            handler.postDelayed(() -> {
                AccessibilityNodeInfo freshRoot = getRootInActiveWindow();
                if (freshRoot != null) {
                    tryAutoTap(freshRoot, prefs);
                    freshRoot.recycle();
                }
            }, 300);
        }
    }

    private void tryAutoTap(AccessibilityNodeInfo root, SharedPreferences prefs) {
        // Strategy 1: Find by button text
        for (String btnText : ACCEPT_BUTTON_TEXTS) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(btnText);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (node.isClickable() || node.isEnabled()) {
                        boolean tapped = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        if (tapped) {
                            int tapCount = prefs.getInt("tap_count", 0);
                            prefs.edit().putInt("tap_count", tapCount + 1).apply();
                            node.recycle();
                            return;
                        }
                        // Try clicking parent if direct click fails
                        AccessibilityNodeInfo parent = node.getParent();
                        if (parent != null && parent.isClickable()) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            int tapCount = prefs.getInt("tap_count", 0);
                            prefs.edit().putInt("tap_count", tapCount + 1).apply();
                            parent.recycle();
                            node.recycle();
                            return;
                        }
                        node.recycle();
                    }
                }
            }
        }

        // Strategy 2: Find by view ID hints
        String[] idHints = {"accept", "btn_accept", "grab_order", "accept_button", "takeOrder"};
        for (String hint : idHints) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(
                    "com.chowdeck.rider:id/" + hint);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo node = nodes.get(0);
                if (node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    int tapCount = prefs.getInt("tap_count", 0);
                    prefs.edit().putInt("tap_count", tapCount + 1).apply();
                    node.recycle();
                    return;
                }
                node.recycle();
            }
        }

        // Strategy 3: Gesture tap at center-bottom of screen (where accept buttons usually are)
        performGestureTap(root);
    }

    private void performGestureTap(AccessibilityNodeInfo root) {
        // Get screen dimensions and tap at the bottom-center
        // This is where most accept/grab buttons appear in delivery apps
        Rect bounds = new Rect();
        root.getBoundsInScreen(bounds);

        int x = bounds.width() / 2;
        int y = (int) (bounds.height() * 0.75); // 75% down the screen

        Path tapPath = new Path();
        tapPath.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(tapPath, 0, 50);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);

        dispatchGesture(builder.build(), null, null);
    }

    private String extractAllText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();

        CharSequence text = node.getText();
        if (text != null) sb.append(text.toString()).append(" ");

        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null) sb.append(contentDesc.toString()).append(" ");

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                sb.append(extractAllText(child));
                child.recycle();
            }
        }
        return sb.toString();
    }

    @Override
    public void onInterrupt() {
        // Service interrupted
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
