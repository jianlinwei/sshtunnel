package org.puff;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

public class PUFFReceiver extends BroadcastReceiver {

	private String host;
	private int port;
	private int localPort;
	private int remotePort;
	private String user;
	private String password;
	private boolean isAutoConnect = false;
	private boolean isAutoSetProxy = false;
	private static final String TAG = "PUFFReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(context);

		isAutoConnect = settings.getBoolean("isAutoConnect", false);

		if (isAutoConnect) {
			try {
				host = settings.getString("host", "");
				user = settings.getString("user", "");
				password = settings.getString("password", "");
				port = 443;
				localPort = 1984;
				remotePort = 3128;
				isAutoSetProxy = settings.getBoolean("isAutoSetProxy", false);
			} catch (Exception e) {
				Log.e(TAG, "Exception when get preferences");
			}

			Intent it = new Intent(context, PUFFService.class);
			Bundle bundle = new Bundle();
			bundle.putString("host", host);
			bundle.putString("user", user);
			bundle.putString("password", password);
			bundle.putInt("port", port);
			bundle.putInt("localPort", localPort);
			bundle.putInt("remotePort", remotePort);
			bundle.putBoolean("isAutoSetProxy", isAutoSetProxy);

			it.putExtras(bundle);
			context.startService(it);
		}
	}

}
