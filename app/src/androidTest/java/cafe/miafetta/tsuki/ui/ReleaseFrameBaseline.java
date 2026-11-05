package cafe.miafetta.tsuki.ui;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

// Android APIs only, so the signed, minified production APK remains the code under measurement.
public final class ReleaseFrameBaseline {
    private final Instrumentation runner;
    private final boolean ablation;
    private volatile Activity host;
    public void onActivityCreated(Activity activity) { host = activity; }
    public ReleaseFrameBaseline(Instrumentation runner, boolean ablation) {
        this.runner = runner;
        this.ablation = ablation;
    }
    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                runner.getUiAutomation().executeShellCommand(command))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String text, int action) {
        if (node == null || !node.isVisibleToUser()) return null;
        if (text != null && (text.contentEquals(node.getText() == null ? "" : node.getText()) ||
                text.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription()))) {
            AccessibilityNodeInfo target = node;
            while (!target.isClickable() && target.getParent() != null) target = target.getParent();
            if (target.isClickable()) return target;
        }
        if (text == null && node.isScrollable() && node.getActionList().stream().anyMatch(a -> a.getId() == action)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), text, action);
            if (found != null) return found;
        }
        return null;
    }
    private AccessibilityNodeInfo node(String text, int action) {
        runner.getUiAutomation().clearCache();
        return find(runner.getUiAutomation().getRootInActiveWindow(), text, action);
    }
    private void click(String text) throws Exception {
        AccessibilityNodeInfo target = node(text, 0);
        if (target == null || !target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) throw new AssertionError("Missing tab " + text);
        Thread.sleep(650);
    }
    private void swipe(float delta, int duration) throws Exception {
        int width = host.getResources().getDisplayMetrics().widthPixels;
        int height = host.getResources().getDisplayMetrics().heightPixels;
        int x = width / 2, y = Math.round(delta < 0 ? height * .78f : height * .32f);
        // HyperOS rejects MotionEvents injected from the release app's process ("injection from package: null"); the shell
        // input command is still allowed and draws the same straight stroke over the same duration.
        shell("input swipe " + x + " " + y + " " + x + " " + Math.round(y + delta) + " " + duration);
        Thread.sleep(80);
    }
    private int traverse(boolean forward) throws Exception {
        int action = forward ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
        int gestures = 0;
        while (node(null, action) != null) {
            if (++gestures > 80) throw new AssertionError("Scroll boundary not reached");
            // Short reverse strokes avoid crossing the pull-to-refresh threshold at the top boundary.
            swipe(forward ? -1100f : 220f, forward ? 650 : 220);
        }
        if (gestures == 0) throw new AssertionError("No scrollable overview content");
        if (!forward) swipe(220f, 300);
        return gestures;
    }
    public String run() throws Exception {
        if (!ablation && (runner.getTargetContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            throw new AssertionError("Release APK required");
        SharedPreferences prefs = runner.getTargetContext().getSharedPreferences("ui", 0);
        boolean hadBlur = prefs.contains("blur"), original = prefs.getBoolean("blur", true);
        File folder = new File(runner.getTargetContext().getExternalFilesDir(null), ablation ? "glass-ablation" : "release-frames");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new AssertionError("Cannot create evidence folder");
        StringBuilder report = new StringBuilder();
        try {
            for (String variant : ablation ? new String[]{"baseline", "topbar", "button", "chip", "dock", "backdrop"}
                    : new String[]{"on", "off"}) {
                boolean blur = !variant.equals("off");
                if (ablation) {
                    Class.forName("cafe.miafetta.tsuki.ui.GlassProbe").getField("disabled").set(null, variant);
                }
                if (!prefs.edit().putBoolean("blur", blur).commit()) throw new AssertionError("Cannot set app blur preference");
                shell("am start -n cafe.miafetta.tsuki/.MainActivity -f 0x10008000");
                long deadline = SystemClock.uptimeMillis() + 10000;
                while (host == null && SystemClock.uptimeMillis() < deadline) Thread.sleep(50);
                if (host == null) throw new AssertionError("Release activity did not start");
                Thread.sleep(3000);
                // Warm up outside every measured interval.
                traverse(true);
                traverse(false);
                for (int round = 1; round <= (ablation ? 1 : 3); round++) {
                    for (String scenario : new String[]{"scroll", "tabs"}) {
                        Thread.sleep(500);
                        shell("dumpsys gfxinfo cafe.miafetta.tsuki reset");
                        int gestures = 0;
                        for (int cycle = 0; cycle < 5; cycle++) {
                            if (scenario.equals("scroll")) {
                                gestures += traverse(true);
                                gestures += traverse(false);
                            } else {
                                click("部件");
                                click("我的");
                                click("概览");
                            }
                        }
                        Thread.sleep(350);
                        String name = variant + "-" + scenario + "-" + round;
                        String stats = shell("dumpsys gfxinfo cafe.miafetta.tsuki framestats");
                        if (!stats.contains("Total frames rendered:")) throw new AssertionError("Missing gfxinfo");
                        Files.writeString(new File(folder, name + ".txt").toPath(), stats);
                        String line = name + " complete; traversals=" + (scenario.equals("scroll") ? 10 : 0) +
                                "; gestures=" + gestures + "\n";
                        report.append(line);
                        Bundle status = new Bundle();
                        status.putString("stream", line);
                        runner.sendStatus(1, status);
                    }
                }
                runner.runOnMainSync(() -> host.finish());
                host = null;
                Thread.sleep(500);
            }
            return "PASS: frame baseline; isolated intervals, 5 down/up cycles or 15 tab switches each; " +
                    "blur preference restored\n" + report;
        } finally {
            if (ablation) Class.forName("cafe.miafetta.tsuki.ui.GlassProbe").getField("disabled").set(null, null);
            SharedPreferences.Editor editor = prefs.edit();
            if (hadBlur) {
                editor.putBoolean("blur", original);
            } else {
                editor.remove("blur");
            }
            if (!editor.commit()) throw new AssertionError("Cannot restore app blur preference");
            if (host != null) runner.runOnMainSync(() -> host.finish());
        }
    }
}
