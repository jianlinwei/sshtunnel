package org.sshtunnel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

public class SSHTunnelReceiver extends BroadcastReceiver {

	private String host;
	private int port;
	private int localPort;
	private int remotePort;
	private String remoteAddress;
	private String user;
	private String password;
	private boolean isAutoConnect = false;
	private boolean isAutoReconnect = false;
	private boolean isAutoSetProxy = false;
	private boolean isSocks = false;
	private static final String TAG = "SSHTunnelReceiver";

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
				port = Integer.valueOf(settings.getString("port", "22"));
				localPort = Integer.valueOf(settings.getString("localPort",
						"1984"));
				remotePort = Integer.valueOf(settings.getString("remotePort",
						"3128"));
				remoteAddress = settings
						.getString("remoteAddress", "127.0.0.1");
				isAutoReconnect = settings.getBoolean("isAutoReconnect", false);
				isAutoSetProxy = settings.getBoolean("isAutoSetProxy", false);
				isSocks = settings.getBoolean("isSocks", false);
			} catch (Exception e) {
				Log.e(TAG, "Exception when get preferences");
			}

			Intent it = new Intent(context, SSHTunnelService.class);
			Bundle bundle = new Bundle();
			bundle.putString("host", host);
			bundle.putString("user", user);
			bundle.putString("password", password);
			bundle.putInt("port", port);
			bundle.putInt("localPort", localPort);
			bundle.putInt("remotePort", remotePort);
			bundle.putString("remoteAddress", remoteAddress);
			bundle.putBoolean("isAutoConnect", isAutoReconnect);
			bundle.putBoolean("isAutoSetProxy", isAutoSetProxy);
			bundle.putBoolean("isSocks", isSocks);

			it.putExtras(bundle);
			context.startService(it);
		}
	}

}
