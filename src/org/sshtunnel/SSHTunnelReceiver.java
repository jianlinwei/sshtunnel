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
	private String passwd;
	private boolean isSaved = false;
	private boolean isAutoStart = false;
	private boolean isAutoReconnect = false;

	@Override
	public void onReceive(Context context, Intent intent) {
		
		SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);

		isSaved = settings.getBoolean("IsSaved", false);
		isAutoStart = settings.getBoolean("IsAutoStart", false);

		if (isSaved && isAutoStart) {
			host = settings.getString("Host", "");
			user = settings.getString("User", "");
			passwd = settings.getString("Password", "");
			port = settings.getInt("Port", 0);
			localPort = settings.getInt("LocalPort", 0);
			remotePort = settings.getInt("RemotePort", 0);
			isAutoReconnect = settings.getBoolean("IsAutoReconnect", false);
			
			
			Intent it = new Intent(context, SSHTunnelService.class);
			Bundle bundle = new Bundle();
			bundle.putString("host", host);
			bundle.putString("user", user);
			bundle.putString("passwd", passwd);
			bundle.putInt("port", port);
			bundle.putInt("localPort", localPort);
			bundle.putInt("remotePort", remotePort);
			bundle.putBoolean("isAutoConnect", isAutoReconnect);

			it.putExtras(bundle);
			context.startService(it);
		}
	}

}
