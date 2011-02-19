package org.sshtunnel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

public class SSHTunnelReceiver extends BroadcastReceiver {

	public static final String PREFS_NAME = "SSHTunnel";

	private String host;
	private int port;
	private int localPort;
	private int remotePort;
	private String user;
	private String password;
	private boolean isSaved = false;
	private boolean isAutoConnect = false;
	private boolean isAutoReconnect = false;
	private boolean isAutoSetProxy = false;

	@Override
	public void onReceive(Context context, Intent intent) {

		SharedPreferences settings = context
				.getSharedPreferences(PREFS_NAME, 0);

		isSaved = settings.getBoolean("isSaved", false);
		isAutoConnect = settings.getBoolean("isAutoConnect", false);

		if (isSaved && isAutoConnect) {
			host = settings.getString("host", "");
			user = settings.getString("user", "");
			password = settings.getString("password", "");
			port = settings.getInt("port", 0);
			localPort = settings.getInt("localPort", 0);
			remotePort = settings.getInt("remotePort", 0);
			isAutoReconnect = settings.getBoolean("isAutoReconnect", false);
			isAutoSetProxy = settings.getBoolean("isAutoSetProxy", false);

			Intent it = new Intent(context, SSHTunnelService.class);
			Bundle bundle = new Bundle();
			bundle.putString("host", host);
			bundle.putString("user", user);
			bundle.putString("password", password);
			bundle.putInt("port", port);
			bundle.putInt("localPort", localPort);
			bundle.putInt("remotePort", remotePort);
			bundle.putBoolean("isAutoConnect", isAutoReconnect);
			bundle.putBoolean("isAutoSetProxy", isAutoSetProxy);

			it.putExtras(bundle);
			context.startService(it);
		}
	}
	

}
