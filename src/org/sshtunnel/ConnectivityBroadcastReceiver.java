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

		if (SSHTunnelService.isConnecting)
			return;
		
		// only switching profiles when needed
		ConnectivityManager manager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = manager.getActiveNetworkInfo();
		if (networkInfo == null) {
			if (Utils.isWorked()) {
				context.stopService(new Intent(context, SSHTunnelService.class));
			}
		}

		// Save current settings first
		ProfileFactory.getProfile();
		ProfileFactory.loadFromPreference();

		String curSSID = null;
		List<Profile> profileList = ProfileFactory.loadAllProfilesFromDao();
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
			if (!Utils.isWorked()) {

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
		
				Utils.notifyConnect();

				Intent it = new Intent(context, SSHTunnelService.class);
				Bundle bundle = new Bundle();
				bundle.putInt(Constraints.ID, profileId);
				it.putExtras(bundle);
				context.startService(it);
			}
		}

	}

}