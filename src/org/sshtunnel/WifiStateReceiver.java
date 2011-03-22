package org.sshtunnel;

import java.util.ArrayList;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;

public class WifiStateReceiver extends BroadcastReceiver {

	public boolean isWorked(Context context, String service) {
		ActivityManager myManager = (ActivityManager) context
				.getSystemService(Context.ACTIVITY_SERVICE);
		ArrayList<RunningServiceInfo> runningService = (ArrayList<RunningServiceInfo>) myManager
				.getRunningServices(30);
		for (int i = 0; i < runningService.size(); i++) {
			if (runningService.get(i).service.getClassName().toString()
					.equals(service)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void onReceive(Context context, Intent intent) {

		Bundle bundle = intent.getExtras();
		int newInt = bundle.getInt("wifi_state");

		if (newInt == WifiManager.WIFI_STATE_DISABLED) {

			if (isWorked(context, SSHTunnel.SERVICE_NAME)) {
				try {
					context.stopService(new Intent(context,
							SSHTunnelService.class));
				} catch (Exception e) {
					// Nothing
				}
			}

			return;
		}
	}

}