package org.sshtunnel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

public class ConnectivityBroadcastReceiver extends BroadcastReceiver {

	private static final String TAG = "ConnectivityBroadcastReceiver";

	public boolean isOnline(Context context) {
		ConnectivityManager manager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = manager.getActiveNetworkInfo();
		if (networkInfo == null)
			return false;
		return true;
	}

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
		String action = intent.getAction();

		if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
			Log.w(TAG, "onReceived() called uncorrectly");
			return;
		}

		Log.e(TAG, "Connection Test");

		TelephonyManager tm = (TelephonyManager) context
				.getSystemService(Context.TELEPHONY_SERVICE);
		String countryCode = tm.getSimCountryIso();

		try {
			if (countryCode != null) {
				Log.d(TAG, "Location: " + countryCode);
				if (countryCode.toLowerCase().equals("cn")) {
					String command = "setprop gsm.sim.operator.numeric 31026\n"
							+ "setprop gsm.operator.numeric 31026\n"
							+ "setprop gsm.sim.operator.iso-country us\n"
							+ "setprop gsm.operator.iso-country us\n"
							+ "setprop gsm.operator.alpha T-Mobile\n"
							+ "setprop gsm.sim.operator.alpha T-Mobile\n"
							+ "kill $(ps | grep vending | tr -s  ' ' | cut -d ' ' -f2)\n"
							+ "rm -rf /data/data/com.android.vending/cache/*\n";
					SSHTunnel.runRootCommand(command);
				}
			}
		} catch (Exception e) {
			// Nothing
		}

		if (!isOnline(context)) {
			if (isWorked(context, SSHTunnel.SERVICE_NAME)) {
				try {
					context.stopService(new Intent(context,
							SSHTunnelService.class));
				} catch (Exception e) {
					// Nothing
				}
			}
		} else {

			// // Wait for connection stable
			// try {
			// Thread.sleep();
			// } catch (InterruptedException ignore) {
			// // Nothing
			// }

			if (!isWorked(context, SSHTunnel.SERVICE_NAME)) {

				while (SSHTunnelService.isStopping) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						break;
					}
				}

				SSHTunnelReceiver sshr = new SSHTunnelReceiver();
				sshr.onReceive(context, intent, false);
			}
		}

	}

}