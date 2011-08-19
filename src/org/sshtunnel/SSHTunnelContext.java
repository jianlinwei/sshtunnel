package org.sshtunnel;

import android.app.Application;
import android.content.Context;

public class SSHTunnelContext extends Application {
	private static Context context;

	public void onCreate() {
		SSHTunnelContext.context = getApplicationContext();
	}

	public static Context getAppContext() {
		return context;
	}

}
