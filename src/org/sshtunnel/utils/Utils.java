package org.sshtunnel.utils;

import java.util.ArrayList;
import java.util.Map;

import org.sshtunnel.R;
import org.sshtunnel.SSHTunnel;
import org.sshtunnel.SSHTunnelContext;
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
import android.preference.PreferenceManager;

public class Utils {

	public static final String SERVICE_NAME = "org.sshtunnel.SSHTunnelService";

	public static String getProfileName(Profile profile) {
		if (profile.getName() == null || profile.getName().equals("")) {
			return SSHTunnelContext.getAppContext().getString(R.string.profile_base) + " "
					+ profile.getId();
		}
		return profile.getName();
	}

	public static boolean isWorked(Context context) {
		ActivityManager myManager = (ActivityManager) context
				.getSystemService(Context.ACTIVITY_SERVICE);
		ArrayList<RunningServiceInfo> runningService = (ArrayList<RunningServiceInfo>) myManager
				.getRunningServices(30);
		for (int i = 0; i < runningService.size(); i++) {
			if (runningService.get(i).service.getClassName().toString()
					.equals(SERVICE_NAME)) {
				return true;
			}
		}
		return false;
	}

	public static void notifyConnect(Context context) {
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

	public static void updateProfiles(Context context) {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(context);

		// Store current settings first
		ProfileFactory.loadFromPreference();

		// Load all profiles
		String[] mProfileValues = settings.getString("profileValues", "")
				.split("\\|");
		
		// Check if needs to update
		Map<String,?> preferences = settings.getAll();
		
		boolean hasOldEdition = false;
		boolean hasNewEdition = false;
		
		for(String p : preferences.keySet()) {
			if (p.contains("1.5."))
				hasNewEdition = true;
			else if (p.contains("1.4.") || p.contains("1.3."))
				hasOldEdition = true;
		}
		
		if (hasNewEdition || !hasOldEdition)
			return;

		// Test on each profile
		for (String p : mProfileValues) {

			if (p.equals("0"))
				continue;

			String profileString = settings.getString(p, "");
			String[] st = profileString.split("\\|");

			ProfileFactory.newProfile();
			Profile profile = ProfileFactory.getProfile();

			profile.setName(settings.getString("profile" + p, ""));

			// tricks for old editions

			try {
				profile.setHost(st[0]);
				profile.setPort(Integer.valueOf(st[1]));
				profile.setUser(st[2]);
				profile.setPassword(st[3]);
				profile.setSocks(st[4].equals("true") ? true : false);
				profile.setLocalPort(Integer.valueOf(st[5]));
				profile.setRemoteAddress(st[6]);
				profile.setRemotePort(Integer.valueOf(st[7]));
			} catch (Exception ignore) {
				// Ignore all exceptions
			}
			
			ProfileFactory.saveToDao();

		}
	}
}