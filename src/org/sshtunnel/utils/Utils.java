package org.sshtunnel.utils;

import java.util.ArrayList;
import java.util.Map;

import org.sshtunnel.R;
import org.sshtunnel.SSHTunnel;
import org.sshtunnel.SSHTunnelContext;
import org.sshtunnel.SSHTunnelService;
import org.sshtunnel.db.Profile;
import org.sshtunnel.db.ProfileFactory;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.util.Log;

public class Utils {

	public static final String SERVICE_NAME = "org.sshtunnel.SSHTunnelService";
	public static final String TAG = "SSHTunnelUtils";

	public static String getProfileName(Profile profile) {
		if (profile.getName() == null || profile.getName().equals("")) {
			return SSHTunnelContext.getAppContext().getString(R.string.profile_base) + " "
					+ profile.getId();
		}
		return profile.getName();
	}
	
    public static Drawable getAppIcon(Context c, int uid) {
        PackageManager pm = c.getPackageManager();
        Drawable appIcon = c.getResources().getDrawable(R.drawable.sym_def_app_icon);
        String[] packages = pm.getPackagesForUid(uid);

        if (packages != null) {
            if (packages.length == 1) {
                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(packages[0], 0);
                    appIcon = pm.getApplicationIcon(appInfo);
                } catch (NameNotFoundException e) {
                	Log.e(TAG, "No package found matching with the uid " + uid);
                }
            }
        } else {
            Log.e(TAG, "Package not found for uid " + uid);
        }

        return appIcon;
    }

	public static boolean isWorked() {
		return SSHTunnelService.isServiceStarted();
	}

	public static void notifyConnect() {
		Context context = SSHTunnelContext.getAppContext();
		NotificationManager notificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		Notification notification = new Notification();
		Intent intent = new Intent(context, SSHTunnel.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		PendingIntent pendIntent = PendingIntent.getActivity(context, 0,
				intent, 0);

		notification.icon = R.drawable.ic_stat;
		notification.tickerText = context.getString(R.string.auto_connecting);
		notification.flags = Notification.FLAG_ONGOING_EVENT;

		notification.setLatestEventInfo(context,
				context.getString(R.string.app_name),
				context.getString(R.string.auto_connecting), pendIntent);
		notificationManager.notify(1, notification);
	}
}