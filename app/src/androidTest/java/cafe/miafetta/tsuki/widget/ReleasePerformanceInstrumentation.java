package cafe.miafetta.tsuki.widget;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;

// Android APIs only: Release obfuscates the app and Kotlin runtime names.
public class ReleasePerformanceInstrumentation extends Instrumentation {
    private final Uri uri = Uri.parse("content://cafe.miafetta.tsuki.widgets");
    private boolean releaseUi;
    private boolean frameBaseline;
    private boolean glassAblation;
    private cafe.miafetta.tsuki.ui.ReleaseFrameBaseline frameProbe;
    private String placement;
    private volatile Activity activity;
    private interface Check { void run() throws Exception; }

    @Override public void onCreate(Bundle arguments) {
        frameBaseline = arguments != null && "true".equals(arguments.getString("frameBaseline"));
        glassAblation = arguments != null && "true".equals(arguments.getString("glassAblation"));
        releaseUi = arguments != null && "true".equals(arguments.getString("releaseUi"));
        placement = arguments == null ? null : arguments.getString("placement");
        super.onCreate(arguments);
        start();
    }
    @Override public void callActivityOnCreate(Activity host, Bundle saved) {
        super.callActivityOnCreate(host, saved);
        activity = host;
        if (frameProbe != null) frameProbe.onActivityCreated(host);
    }

    private AccessibilityNodeInfo find(String text, AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (text.contentEquals(node.getText() == null ? "" : node.getText()) ||
            text.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(text, node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }
    private AccessibilityNodeInfo await(String text) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        while (SystemClock.uptimeMillis() < deadline) {
            getUiAutomation().clearCache();
            AccessibilityNodeInfo node = find(text, getUiAutomation().getRootInActiveWindow());
            if (node != null) return node;
            Thread.sleep(100);
        }
        throw new AssertionError("Missing " + text);
    }
    private void click(String text) throws Exception {
        AccessibilityNodeInfo node = await(text);
        while (!node.isClickable() && node.getParent() != null) node = node.getParent();
        if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) throw new AssertionError("Cannot click " + text);
        Thread.sleep(600);
    }

    private String verifyReleaseUi() throws Exception {
        SharedPreferences prefs = getTargetContext().getSharedPreferences("ui", 0);
        String originalTheme = prefs.getString("theme", "SYSTEM");
        boolean originalBlur = prefs.getBoolean("blur", true);
        boolean hadTheme = prefs.contains("theme"), hadBlur = prefs.contains("blur");
        String before = call("overview", Bundle.EMPTY).getString("json");
        try {
            try (android.os.ParcelFileDescriptor.AutoCloseInputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                getUiAutomation().executeShellCommand("am start -n cafe.miafetta.tsuki/.MainActivity -f 0x10008000"))) {
                byte[] buffer = new byte[1024];
                while (input.read(buffer) != -1) { }
            }
            click("账号");
            if (new JSONObject(before).getJSONArray("accounts").length() > 0) {
                click("删除");
                await("删除账号");
                click("取消");
                JSONArray afterAccounts = new JSONObject(call("overview", Bundle.EMPTY).getString("json")).getJSONArray("accounts");
                if (!afterAccounts.toString().equals(new JSONObject(before).getJSONArray("accounts").toString()))
                    throw new AssertionError("Cancel changed accounts");
            }
            click("设置");
            click("深色模式");
            String chosen = "DARK".equals(originalTheme) ? "LIGHT" : "DARK";
            click("LIGHT".equals(chosen) ? "关闭" : "开启");
            click("底栏模糊");
            if (!chosen.equals(prefs.getString("theme", "")) || prefs.getBoolean("blur", originalBlur) == originalBlur)
                throw new AssertionError("Settings actions did not persist");
            Activity previous = activity;
            runOnMainSync(previous::recreate);
            long deadline = SystemClock.uptimeMillis() + 10_000;
            while (activity == previous && SystemClock.uptimeMillis() < deadline) Thread.sleep(100);
            if (activity == previous) throw new AssertionError("Release activity did not recreate");
            await("外观");
            if (!chosen.equals(prefs.getString("theme", "")) || prefs.getBoolean("blur", originalBlur) == originalBlur)
                throw new AssertionError("Settings lost on recreation");
            return "PASS: actual Release delete cancellation and settings persistence on normal portrait recreation\n";
        } finally {
            SharedPreferences.Editor editor = prefs.edit();
            if (hadTheme) {
                editor.putString("theme", originalTheme);
            } else {
                editor.remove("theme");
            }
            if (hadBlur) {
                editor.putBoolean("blur", originalBlur);
            } else {
                editor.remove("blur");
            }
            if (!editor.commit()) throw new AssertionError("Original settings restore failed");
            if (activity != null) runOnMainSync(() -> activity.finish());
        }
    }

    private Bundle call(String method, Bundle extras) {
        Bundle result = getTargetContext().getContentResolver().call(uri, method, null, extras);
        if (result == null || result.getBoolean("error")) throw new AssertionError(method + " failed");
        return result;
    }

    private String verifyPlacement() throws Exception {
        JSONObject data = new JSONObject(call("overview", Bundle.EMPTY).getString("json"));
        JSONArray widgets = data.getJSONArray("widgets"), accounts = data.getJSONArray("accounts");
        StringBuilder result = new StringBuilder("PASS placed widget bindings\n");
        for (String entry : placement.split(",")) {
            String[] expected = entry.split(":");
            JSONObject widget = null;
            for (int i = 0; i < widgets.length(); i++)
                if (widgets.getJSONObject(i).getInt("id") == Integer.parseInt(expected[0])) widget = widgets.getJSONObject(i);
            if (widget == null || !expected[1].equals(widget.getString("theme"))) {
                throw new AssertionError("Missing widget/theme: " + entry);
            }
            boolean binding = false;
            for (int i = 0; i < accounts.length(); i++) {
                JSONObject account = accounts.getJSONObject(i);
                if (!account.getString("id").equals(widget.getString("accountId"))) continue;
                JSONArray roles = account.getJSONArray("roles");
                for (int j = 0; j < roles.length(); j++)
                    binding |= roles.getJSONObject(j).getString("id").equals(widget.getString("characterId"));
            }
            if (!binding || widget.isNull("snapshot")) throw new AssertionError("Missing binding/snapshot: " + entry);
            result.append("id=").append(expected[0]).append(" theme=").append(expected[1]).append(" account/role=true snapshot=true\n");
        }
        return result.toString();
    }

    private String measure(String name, Check action) throws Exception {
        for (int i = 0; i < 2; i++) action.run();
        double[] samples = new double[9];
        for (int i = 0; i < samples.length; i++) {
            long start = SystemClock.elapsedRealtimeNanos();
            action.run();
            samples[i] = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0;
        }
        Arrays.sort(samples);
        return name + " medianMs=" + samples[4] + " maxMs=" + samples[8] + "\n";
    }

    @Override public void onStart() {
        Bundle output = new Bundle();
        try {
            StringBuilder report = new StringBuilder("PASS: Release provider performance and preview transport\n");
            if (!glassAblation && (getTargetContext().getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                throw new AssertionError("Performance acceptance requires Release");
            if (frameBaseline) {
                frameProbe = new cafe.miafetta.tsuki.ui.ReleaseFrameBaseline(this, glassAblation);
                output.putString("result", frameProbe.run());
                finish(Activity.RESULT_OK, output);
                return;
            }
            if (placement != null) {
                output.putString("result", verifyPlacement());
                finish(Activity.RESULT_OK, output);
                return;
            }
            if (releaseUi) report.append(verifyReleaseUi());
            report.append(measure("overview", () -> call("overview", Bundle.EMPTY)));
            report.append(measure("characters", () -> call("characters", Bundle.EMPTY)));
            JSONObject overview = new JSONObject(call("overview", Bundle.EMPTY).getString("json"));
            JSONArray accounts = overview.getJSONArray("accounts"), widgets = overview.getJSONArray("widgets");
            report.append("accounts=").append(accounts.length()).append(" widgets=").append(widgets.length()).append('\n');
            String[] bindings = new String[widgets.length()];
            for (int i = 0; i < widgets.length(); i++) {
                JSONObject widget = widgets.getJSONObject(i);
                bindings[i] = widget.getInt("id") + ":" + widget.optString("accountId") + ":" +
                    widget.optString("characterId") + ":" + widget.optString("theme") + ":" + widget.optString("providerId");
            }
            Arrays.sort(bindings);
            report.append("bindingsChecksum=").append(Arrays.hashCode(bindings)).append('\n');
            if (accounts.length() > 0 && accounts.getJSONObject(0).getJSONArray("roles").length() > 0) {
                JSONObject account = accounts.getJSONObject(0);
                Bundle extras = new Bundle();
                extras.putString("accountId", account.getString("id"));
                extras.putString("characterId", account.getJSONArray("roles").getJSONObject(0).getString("id"));
                report.append(measure("cached-dashboard", () -> call("dashboard", extras)));
            }
            int[] checksums = new int[2];
            int index = 0;
            for (String theme : new String[]{"LIGHT", "DARK"}) {
                final int slot = index++;
                final boolean[] seen = {false};
                report.append(measure("preview-" + theme, () -> {
                    Bundle extras = new Bundle();
                    extras.putString("theme", theme);
                    Bundle result = call("preview", extras);
                    Bitmap bitmap = result.getParcelable("bitmap", Bitmap.class);
                    if (bitmap == null) {
                        byte[] bytes = result.getByteArray("bytes");
                        if (bytes != null) bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    }
                    if (bitmap == null) throw new AssertionError("Missing preview");
                    try {
                        if (bitmap.getWidth() != 1028 || bitmap.getHeight() != 462) throw new AssertionError("Preview dimensions");
                        int[] pixels = new int[1028 * 462];
                        bitmap.getPixels(pixels, 0, 1028, 0, 0, 1028, 462);
                        int checksum = Arrays.hashCode(pixels);
                        if (seen[0] && checksums[slot] != checksum) throw new AssertionError("Identical requests changed preview pixels");
                        checksums[slot] = checksum;
                        seen[0] = true;
                    } finally { bitmap.recycle(); }
                }));
            }
            if (checksums[0] == checksums[1]) throw new AssertionError("Preview palettes are identical");
            report.append("previewChecksums=").append(Arrays.toString(checksums));
            output.putString("result", report.toString());
            finish(Activity.RESULT_OK, output);
        } catch (Throwable failure) {
            output.putString("failure", android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, output);
        }
    }
}
