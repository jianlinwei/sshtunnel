package org.sshtunnel;

import java.util.List;

import org.sshtunnel.db.Profile;
import org.sshtunnel.db.ProfileFactory;
import org.sshtunnel.utils.Constraints;
import org.sshtunnel.utils.Utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.ksmaze.android.preference.ListPreferenceMultiSelect;

public class ConnectivityBroadcastReceiver extends BroadcastReceiver {

	private static final String TAG = "ConnectivityBroadcastReceiver";

	public String onlineSSID(Context context, String ssid) {
		String ssids[] = ListPreferenceMultiSelect.parseStoredValue(ssid);
		if (ssids == null)
			return null;
		if (ssids.length < 1)
			return null;
		ConnectivityManager manager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = manager.getActiveNetworkInfo();
		if (networkInfo == null)
			return null;
		if (!networkInfo.getTypeName().equals("WIFI")) {
			for (String item : ssids) {
				if (item.equals("2G/3G"))
					return "2G/3G";
			}
			return null;
		}
		WifiManager wm = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		WifiInfo wInfo = wm.getConnectionInfo();
		if (wInfo == null)
			return null;
		String current = wInfo.getSSID();
		if (current == null || current.equals(""))
			return null;
		for (String item : ssids) {
			if (item.equals(current))
				return item;
		}
		return null;
	}

	@Override
	public synchronized void onReceive(Context context, Intent intent) {
		String action = intent.getAction();

		if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
			Log.w(TAG, "onReceived() called uncorrectly");
			return;
		}

		Log.d(TAG, "Connection Test");

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(context);

		boolean isMarketEnable = settings.getBoolean("isMarketEnable", false);

		if (isMarketEnable) {
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
								+ "chmod 777 /data/data/com.android.vending/shared_prefs\n"
								+ "chmod 666 /data/data/com.android.vending/shared_prefs/vending_preferences.xml\n"
								+ "setpref com.android.vending vending_preferences boolean metadata_paid_apps_enabled true\n"
								+ "chmod 660 /data/data/com.android.vending/shared_prefs/vending_preferences.xml\n"
								+ "chmod 771 /data/data/com.android.vending/shared_prefs\n"
								+ "setown com.android.vending /data/data/com.android.vending/shared_prefs/vending_preferences.xml\n"
								+ "kill $(ps | grep vending | tr -s  ' ' | cut -d ' ' -f2)\n"
								+ "rm -rf /data/data/com.android.vending/cache/*\n";
						SSHTunnel.runRootCommand(command);
					}
				}
			} catch (Exception e) {
				// Nothing
			}
		}

		if (SSHTunnelService.isConnecting)
			return;
		
		// only switching profiles when needed
		ConnectivityManager manager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = manager.getActiveNetworkInfo();
		if (networkInfo == null) {
			if (Utils.isWorked(context)) {
				context.stopService(new Intent(context, SSHTunnelService.class));
			}
		} else {
			String lastSSID = settings.getString("lastSSID", "-1");

			if (networkInfo.getTypeName().equals("WIFI")) {
				if (!lastSSID.equals("-1")) {
					WifiManager wm = (WifiManager) context
							.getSystemService(Context.WIFI_SERVICE);
					WifiInfo wInfo = wm.getConnectionInfo();
					if (wInfo != null) {
						String current = wInfo.getSSID();
						if (current != null && !current.equals(lastSSID)) {
							if (Utils.isWorked(context)) {
								context.stopService(new Intent(context,
										SSHTunnelService.class));
							}
						}
					}
				}
			} else {
				if (!lastSSID.equals("2G/3G")) {
					if (Utils.isWorked(context)) {
						context.stopService(new Intent(context,
								SSHTunnelService.class));
					}
				}
			}
		}

		// Save current settings first
		ProfileFactory.getProfile(context);
		ProfileFactory.loadFromPreference(context);

		String curSSID = null;
		List<Profile> profileList = ProfileFactory.loadFromDao(context);
		int profileId = -1;

		// Test on each profile
		for (Profile profile : profileList) {

			curSSID = onlineSSID(context, profile.getSsid());
			if (profile.isAutoConnect() && curSSID != null) {

				// Then switch profile values
				profileId = profile.getId();
				break;
			}
		}

		if (curSSID != null && profileId != -1) {
			if (!Utils.isWorked(context)) {

				Editor ed = settings.edit();
				ed.putString("lastSSID", curSSID);
				ed.commit();
				
				while (SSHTunnelService.isStopping) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						break;
					}
				}
		
				Utils.notifyConnect(context);

				Intent it = new Intent(context, SSHTunnelService.class);
				Bundle bundle = new Bundle();
				bundle.putInt(Constraints.ID, profileId);
				it.putExtras(bundle);
				context.startService(it);
			}
		}

	}

}