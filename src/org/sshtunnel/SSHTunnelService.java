package org.sshtunnel;

import java.io.BufferedReader;
import java.io.DataOutputStream;

import java.io.FileReader;
import java.io.IOException;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionMonitor;
import com.trilead.ssh2.DebugLogger;
import com.trilead.ssh2.log.Logger;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;

import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class SSHTunnelService extends Service implements ConnectionMonitor {

	private Notification notification;
	private NotificationManager notificationManager;
	private Intent intent;
	private PendingIntent pendIntent;

	private static final String TAG = "SSHTunnel";
	public static final String PREFS_NAME = "SSHTunnel";

	private String host;
	private int port;
	private int localPort;
	private int remotePort;
	private String user;
	private String passwd;

	private final static int AUTH_TRIES = 2;

	private Connection connection;

	private boolean connected = false;
	private boolean authenticated = false;


	public void connectionLost(Throwable reason) {
		stopSelf();
	}

	private void onDisconnect() {

		notification.icon = R.drawable.icon;
		notification.tickerText = "Port Forward Stop";
		notification.defaults = Notification.DEFAULT_SOUND;
		notification.setLatestEventInfo(this,
				"SSHTunnel", "SSHTunnel Service has been stopped.", pendIntent);
		notificationManager.notify(0, notification);
		
		connected = false;
		SSHTunnel.isConnected = false;

		if (connection != null) {
			connection.close();
			connection = null;
		}

	}

	@Override
	public void onCreate() {
		super.onCreate();
		notificationManager = (NotificationManager) this
				.getSystemService(NOTIFICATION_SERVICE);
		
		intent = new Intent(this, SSHTunnel.class);
		pendIntent = PendingIntent.getActivity(this, 0, intent, 0);
		notification = new Notification();
			
	}

	/** Called when the activity is closed. */
	@Override
	public void onDestroy() {
		onDisconnect();
		super.onDestroy();
	}

	// This is the old onStart method that will be called on the pre-2.0
	// platform. On 2.0 or later we override onStartCommand() so this
	// method will not be called.
	@Override
	public void onStart(Intent intent, int startId) {
		handleCommand(intent);
		
		notification.icon = R.drawable.icon;
		notification.tickerText = "Port Forward Successful! Enjoy!";
		notification.defaults = Notification.DEFAULT_SOUND;
		notification.setLatestEventInfo(this,
				"SSHTunnel", "SSHTunnel Service is running now.", pendIntent);
		notificationManager.notify(0, notification);
		SSHTunnel.isConnected = true;
		
		super.onStart(intent, startId);
	}
	
	/** Called when the activity is first created. */
	public void handleCommand(Intent it) {

		Log.e(TAG, "Service Start");

		Bundle bundle = it.getExtras();
		host = bundle.getString("host");
		user = bundle.getString("user");
		passwd = bundle.getString("passwd");
		port = bundle.getInt("port");
		localPort = bundle.getInt("localPort");
		remotePort = bundle.getInt("remotePort");

		try {
			connect();
		} catch (Exception e) {
			Log.e(TAG, "Forward Failed" + e.getMessage());
		}
	}

	public void connect() {

		connection = new Connection(host, port);
		connection.addConnectionMonitor(this);

		try {
			connection.setCompression(true);
		} catch (IOException e) {
			Log.e(TAG, "Could not enable compression!", e);
		}

		try {
			/*
			 * Uncomment when debugging SSH protocol:
			 */

			/*
			 * DebugLogger logger = new DebugLogger() {
			 * 
			 * public void log(int level, String className, String message) {
			 * Log.d("SSH", message); }
			 * 
			 * };
			 * 
			 * Logger.enabled = true; Logger.logger = logger;
			 */
			
			
			 DebugLogger logger = new DebugLogger() {
			 
			 public void log(int level, String className, String message) {
			 Log.e("SSH", message); }
			 
			 };
			 
			 Logger.enabled = true; 
			 Logger.logger = logger;
			 

			connection.connect();
			connected = true;

		} catch (IOException e) {
			Log.e(TAG,
					"Problem in SSH connection thread during authentication", e);

			// Display the reason in the text.

			onDisconnect();
			return;
		}

		try {
			// enter a loop to keep trying until authentication
			int tries = 0;
			while (connected && !connection.isAuthenticationComplete()
					&& tries++ < AUTH_TRIES) {
				authenticate();

				// sleep to make sure we dont kill system
				Thread.sleep(1000);
			}
		} catch (Exception e) {
			Log.e(TAG,
					"Problem in SSH connection thread during authentication", e);
		}

	}

	private void authenticate() {
		try {
			if (connection.authenticateWithNone(user)) {
				finishConnection();
				return;
			}
		} catch (Exception e) {
			Log.d(TAG, "Host does not support 'none' authentication.");
		}

		try {

			if (connection.authenticateWithPassword(user, passwd))
				finishConnection();

		} catch (IllegalStateException e) {
			Log.e(TAG,
					"Connection went away while we were trying to authenticate",
					e);
			return;
		} catch (Exception e) {
			Log.e(TAG, "Problem during handleAuthentication()", e);
		}
	}

	/**
	 * Internal method to request actual PTY terminal once we've finished
	 * authentication. If called before authenticated, it will just fail.
	 */
	private void finishConnection() {
		authenticated = true;

		try {
			if (enablePortForward())
				Log.e(TAG, "Forward Successful");

		} catch (Exception e) {
			Log.e(TAG, "Error setting up port forward during connect", e);
		}

	}

	public boolean enablePortForward() {

		if (!authenticated)
			return false;

		/*
		 * DynamicPortForwarder dpf = null;
		 * 
		 * try { dpf = connection.createDynamicPortForwarder(new
		 * InetSocketAddress( InetAddress.getLocalHost(), 1984)); } catch
		 * (Exception e) { Log.e(TAG, "Could not create dynamic port forward",
		 * e); return false; }
		 */

		// LocalPortForwarder lpf1 = null;
		try {
			connection.createLocalPortForwarder(localPort, "127.0.0.1",
					remotePort);
		} catch (Exception e) {
			Log.e(TAG, "Could not create local port forward", e);
			return false;
		}

		return true;
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

}
