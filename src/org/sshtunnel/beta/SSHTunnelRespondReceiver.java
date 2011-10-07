package org.sshtunnel.beta;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

public class SSHTunnelRespondReceiver extends BroadcastReceiver {

	private static final String TAG = "SSHTunnelReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		synchronized (SSHTunnelService.notify_lock) {
			SSHTunnelService.notify_type = intent.getIntExtra("type", -1);
			SSHTunnelService.notify_status = intent.getStringExtra("status");
			SSHTunnelService.notify_lock.notify();
		}
		Log.d(TAG, "received: " + SSHTunnelService.notify_type + " "
				+ SSHTunnelService.notify_status);
	}

}
