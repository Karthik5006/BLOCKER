package app.blocker;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

public final class HomeActivity extends Activity {
    private final List<LauncherActivityInfo> apps = new ArrayList<>();
    private ArrayAdapter<String> labels;
    private Button focus;
    private LauncherApps launcher;
    private GuardRoutes routes;

    static boolean isDefault(Context context) {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager roles = context.getSystemService(RoleManager.class);
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roles.isRoleHeld(RoleManager.ROLE_HOME);
            }
        }
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = context.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return info != null && info.activityInfo != null
            && context.getPackageName().equals(info.activityInfo.packageName);
    }

    static void chooseHome(Activity activity) {
        Intent request = new Intent(Settings.ACTION_HOME_SETTINGS);
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager roles = activity.getSystemService(RoleManager.class);
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)
                    && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                request = roles.createRequestRoleIntent(RoleManager.ROLE_HOME);
            }
        }
        try {
            activity.startActivityForResult(request, 30);
        } catch (ActivityNotFoundException unavailable) {
            Toast.makeText(activity, "Choose BLOCKER in Settings → Default apps → Home app.", Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        launcher = getSystemService(LauncherApps.class);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setFitsSystemWindows(true);
        int inset = Math.round(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(inset, inset, inset, inset);
        focus = new Button(this);
        focus.setAllCaps(false);
        focus.setOnClickListener(v -> openTimer(false));
        layout.addView(focus);
        labels = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ListView list = new ListView(this);
        list.setAdapter(labels);
        list.setOnItemClickListener((parent, view, position, id) -> launch(apps.get(position)));
        layout.addView(list);
        setContentView(layout);
    }

    @Override protected void onResume() {
        super.onResume();
        State.restoreClock(this);
        if (State.pending(this) && isDefault(this)) State.startLauncherSession(this);
        if (State.expired(this)) State.finishSession(this);
        focus.setText(State.active(this) ? "View focus timer" : "Set up focus session");
        routes = new GuardRoutes(AppCatalog.essential(this));
        apps.clear();
        apps.addAll(launcher.getActivityList(null, Process.myUserHandle()));
        apps.removeIf(app -> getPackageName().equals(app.getComponentName().getPackageName()));
        Collator order = Collator.getInstance();
        apps.sort((left, right) -> order.compare(left.getLabel().toString(), right.getLabel().toString()));
        labels.clear();
        for (LauncherActivityInfo app : apps) labels.add(app.getLabel().toString());
    }

    private void openTimer(boolean blocked) {
        startActivity(new Intent(this, MainActivity.class).putExtra("blocked", blocked)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void launch(LauncherActivityInfo app) {
        if (State.active(this) && routes.shouldBlock(app.getComponentName().getPackageName(), State.selected(this))) {
            openTimer(true);
            return;
        }
        try {
            launcher.startMainActivity(app.getComponentName(), app.getUser(), null, null);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, "This app is unavailable.", Toast.LENGTH_SHORT).show();
        }
    }
}
