package org.sshtunnel;

import java.io.BufferedReader;
import java.io.DataOutputStream;

import java.io.FileReader;
import java.io.IOException;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionMonitor;

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
	private boolean isAutoReconnect = false;
	

	private final static int AUTH_TRIES = 2;
	private final static int RECONNECT_TRIES = 2;

	private Connection connection;

	private boolean connected = false;
	private boolean authenticated = false;
	public static boolean isConnected = false;

	// Flag indicating if this is an ARMv6 device (-1: unknown, 0: no, 1: yes)
	private static int isARMv6 = -1;

	/**
	 * Check if this is an ARMv6 device
	 * 
	 * @return true if this is ARMv6
	 */
	private static boolean isARMv6() {
		if (isARMv6 == -1) {
			BufferedReader r = null;
			try {
				isARMv6 = 0;
				r = new BufferedReader(new FileReader("/proc/cpuinfo"));
				for (String line = r.readLine(); line != null; line = r
						.readLine()) {
					if (line.startsWith("Processor") && line.contains("ARMv6")) {
						isARMv6 = 1;
						break;
					} else if (line.startsWith("CPU architecture")
							&& (line.contains("6TE") || line.contains("5TE"))) {
						isARMv6 = 1;
						break;
					}
				}
			} catch (Exception ex) {
			} finally {
				if (r != null)
					try {
						r.close();
					} catch (Exception ex) {
					}
			}
		}
		return (isARMv6 == 1);
	}

	public void connectionLost(Throwable reason) {
		try {
			Thread.sleep(5000);
		} catch (Exception ignore) {

		}
		if (isAutoReconnect && isConnected) {
			for (int reconNum = 1; reconNum <= RECONNECT_TRIES; reconNum++) {
				connected = false;

				if (connection != null) {
					connection.close();
					connection = null;
				}

				try {

					// Reconnect now.
					connect();

				} catch (Exception e) {
					Log.e(TAG, "Forward Failed" + e.getMessage());
					continue;
				}

				notification.icon = R.drawable.icon;
				notification.tickerText = "Auto Reconnected.";
				notification.defaults = Notification.DEFAULT_SOUND;
				notification.setLatestEventInfo(this, "SSHTunnel",
						"Reconnected for " + reconNum + " times", pendIntent);
				notificationManager.notify(0, notification);
				return;
			}

		} else
			stopSelf();
	}

	public static boolean runRootCommand(String command) {
		Process process = null;
		DataOutputStream os = null;
		try {
			process = Runtime.getRuntime().exec("su");
			os = new DataOutputStream(process.getOutputStream());
			os.writeBytes(command + "\n");
			os.writeBytes("exit\n");
			os.flush();
			process.waitFor();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
			return false;
		} finally {
			try {
				if (os != null) {
					os.close();
				}
				process.destroy();
			} catch (Exception e) {
				// nothing
			}
		}
		return true;
	}

	private void onDisconnect() {

		notification.icon = R.drawable.icon;
		notification.tickerText = "Port Forward Stop";
		notification.defaults = Notification.DEFAULT_SOUND;
		notification.setLatestEventInfo(this, "SSHTunnel",
				"SSHTunnel Service has been stopped.", pendIntent);
		notificationManager.notify(0, notification);

		connected = false;
		isConnected = false;

		if (connection != null) {
			connection.close();
			connection = null;
		}

		if (isARMv6()) {
			runRootCommand("/data/data/org.sshtunnel/iptables_g1 -t nat -F OUTPUT");
		} else {
			runRootCommand("/data/data/org.sshtunnel/iptables_n1 -t nat -F OUTPUT");
		}

		runRootCommand("/data/data/org.sshtunnel/proxy.sh stop");

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
		if (isConnected) {
			onDisconnect();
			super.onDestroy();
		}
	}

	// This is the old onStart method that will be called on the pre-2.0
	// platform. On 2.0 or later we override onStartCommand() so this
	// method will not be called.
	@Override
	public void onStart(Intent intent, int startId) {
		if (handleCommand(intent)) {
			notification.icon = R.drawable.icon;
			notification.tickerText = "Port Forward Successful! Enjoy!";
			notification.defaults = Notification.DEFAULT_SOUND;
			notification.setLatestEventInfo(this, "SSHTunnel",
					"SSHTunnel Service is running now.", pendIntent);
			notificationManager.notify(0, notification);
			isConnected = true;

			super.onStart(intent, startId);
		} else {
			notification.icon = R.drawable.icon;
			notification.tickerText = "Port Forward Failed.";
			notification.defaults = Notification.DEFAULT_SOUND;
			notification.setLatestEventInfo(this, "SSHTunnel",
					"Please check your network or settings.", pendIntent);
			notificationManager.notify(0, notification);
			isConnected = false;
			stopSelf();
		}
	}

	/** Called when the activity is first created. */
	public boolean handleCommand(Intent it) {

		Log.e(TAG, "Service Start");

		Bundle bundle = it.getExtras();
		host = bundle.getString("host");
		user = bundle.getString("user");
		passwd = bundle.getString("passwd");
		port = bundle.getInt("port");
		localPort = bundle.getInt("localPort");
		remotePort = bundle.getInt("remotePort");
		isAutoReconnect = bundle.getBoolean("isAutoReconnect");

		try {
			connect();
		} catch (Exception e) {
			Log.e(TAG, "Forward Failed" + e.getMessage());
			return false;
		}
		return true;
	}

	public void connect() throws Exception {

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

			connection.connect();

		} catch (Exception e) {
			Log.e(TAG,
					"Problem in SSH connection thread during authentication", e);

			// Display the reason in the text.

			throw(e);
		}
		
		connected = true;

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
			throw(e);
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
			if (enablePortForward()) {
				Log.e(TAG, "Forward Successful");

				runRootCommand("/data/data/org.sshtunnel/proxy.sh start "
						+ localPort);

				if (isARMv6()) {
					runRootCommand("/data/data/org.sshtunnel/iptables_g1 -t nat -A OUTPUT -p tcp "
							+ "--dport 80 -j REDIRECT --to-ports 8123");
					runRootCommand("/data/data/org.sshtunnel/iptables_g1 -t nat -A OUTPUT -p tcp "
							+ "--dport 443 -j REDIRECT --to-ports 8124");
					runRootCommand("/data/data/org.sshtunnel/iptables_g1 -t nat -A OUTPUT "
							+ "--dport 53 -j REDIRECT --to-ports 1053");
				} else {
					runRootCommand("/data/data/org.sshtunnel/iptables_n1 -t nat -A OUTPUT -p tcp "
							+ "--dport 80 -j REDIRECT --to-ports 8123");
					runRootCommand("/data/data/org.sshtunnel/iptables_n1 -t nat -A OUTPUT -p tcp "
							+ "--dport 443 -j REDIRECT --to-ports 8124");
					runRootCommand("/data/data/org.sshtunnel/iptables_g1 -t nat -A OUTPUT "
							+ "--dport 53 -j REDIRECT --to-ports 1053");
				}
			}

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
			connection.createLocalPortForwarder(1053, "8.8.8.8", 53);
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
