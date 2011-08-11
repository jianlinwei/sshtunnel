package org.sshtunnel.utils;

import java.util.ArrayList;

import org.sshtunnel.R;
import org.sshtunnel.SSHTunnel;
import org.sshtunnel.db.Profile;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;

public class Utils {
	
	public static final String SERVICE_NAME = "org.sshtunnel.SSHTunnelService";
	
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
		PendingIntent pendIntent = PendingIntent.getActivity(context, 0, intent, 0);
		
		notification.icon = R.drawable.ic_stat;
		notification.tickerText = context.getString(R.string.auto_connecting);
		notification.flags = Notification.FLAG_ONGOING_EVENT;
		
		notification.setLatestEventInfo(context, context.getString(R.string.app_name),
				context.getString(R.string.auto_connecting), pendIntent);
		notificationManager.notify(1, notification);
	}
	
	public static String getProfileName(Context context, Profile profile) {
		if (profile.getName() == null || profile.getName().equals("")) {
			return context.getString(R.string.profile_base) + " " + profile.getId();
		}
		return profile.getName();
	}
}