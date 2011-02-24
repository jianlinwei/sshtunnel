package org.sshtunnel;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionMonitor;
import com.trilead.ssh2.LocalPortForwarder;

public class SSHTunnelService extends Service implements ConnectionMonitor {

	private Notification notification;
	private NotificationManager notificationManager;
	private Intent intent;
	private PendingIntent pendIntent;

	private static final String TAG = "SSHTunnel";
	private SharedPreferences settings = null;

	private String host;
	private int port;
	private int localPort;
	private int remotePort;
	private String user;
	private String password;
	private boolean isAutoReconnect = false;
	private boolean isAutoSetProxy = false;
	private LocalPortForwarder lpf1 = null;
//	private LocalPortForwarder lpf2 = null;
	private DNSServer dnsServer = null;

	private final static int AUTH_TRIES = 2;
	private final static int RECONNECT_TRIES = 3;

	private Connection connection;

	private boolean connected = false;

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

	private void authenticate() {
		try {
			if (connection.authenticateWithNone(user)) {
				return;
			}
		} catch (Exception e) {
			Log.d(TAG, "Host does not support 'none' authentication.");
		}

		try {

			if (connection.authenticateWithPassword(user, password))
				return;

		} catch (IllegalStateException e) {
			Log.e(TAG,
					"Connection went away while we were trying to authenticate",
					e);
		} catch (Exception e) {
			Log.e(TAG, "Problem during handleAuthentication()", e);
		}
	}

	public boolean connect() {

		connection = new Connection(host, port);
		connection.addConnectionMonitor(this);

		try {

			connection.setCompression(true);
			connection.setTCPNoDelay(true);

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

			connection.connect(null, 10 * 1000, 10 * 1000);
			connected = true;

		} catch (Exception e) {
			Log.e(TAG,
					"Problem in SSH connection thread during authentication", e);

			// Display the reason in the text.

			return false;
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
			return false;
		}

		if (connection.isAuthenticationComplete())
			return finishConnection();

		return false;

	}

	public void connectionLost(Throwable reason) {

		SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");

		if (reason != null) {
			if (reason.getMessage().contains(
					"There was a problem during connect")) {
				Log.e(TAG, "connection lost", reason);
				return;
			} else if (reason.getMessage().contains(
					"Closed due to user request")) {
				Log.e(TAG, "connection lost", reason);
				return;
			}
			Log.e(TAG, "connection lost: " + reason.getMessage());
		} else {
			return;
		}

		if (isAutoReconnect && connected) {
			for (int reconNum = 1; reconNum <= RECONNECT_TRIES; reconNum++) {

				if (!isOnline()) {

					connected = false;
					notifyAlert(
							getString(R.string.auto_reconnected) + " "
									+ df.format(new Date()),
							getString(R.string.reconnect_fail),
							Notification.FLAG_AUTO_CANCEL);
					stopSelf();
					return;
				}

				onDisconnect();

				if (!connect()) {

					try {
						Thread.sleep(5000 * reconNum);
					} catch (Exception ignore) {
						// Nothing
					}

					continue;
				}

				notifyAlert(
						getString(R.string.auto_reconnected) + " "
								+ df.format(new Date()),
						getString(R.string.reconnect_success));
				return;
			}

			connected = false;
			notifyAlert(
					getString(R.string.auto_reconnected) + " "
							+ df.format(new Date()),
					getString(R.string.reconnect_fail),
					Notification.FLAG_AUTO_CANCEL);
			stopSelf();

		} else

			stopSelf();
	}

	public boolean enablePortForward() {

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
			lpf1 = connection.createLocalPortForwarder(localPort, "127.0.0.1",
					remotePort);
//			lpf2 = connection.createLocalPortForwarder(5353, "8.8.8.8", 53);
		} catch (Exception e) {
			Log.e(TAG, "Could not create local port forward", e);
			return false;
		}

		return true;
	}

	/**
	 * Internal method to request actual PTY terminal once we've finished
	 * authentication. If called before authenticated, it will just fail.
	 */
	private boolean finishConnection() {

		if (enablePortForward()) {
			Log.e(TAG, "Forward Successful");
			// XXX: Flush iptables first?
			if (isARMv6()) {
				String cmd = "/data/data/org.sshtunnel/iptables_g1 -t nat -D OUTPUT -p tcp "
						+ "--dport 80 -j REDIRECT --to-ports 8123\n"
						+ "/data/data/org.sshtunnel/iptables_g1 -t nat -D OUTPUT -p tcp "
						+ "--dport 443 -j REDIRECT --to-ports 8124\n"
						+ "/data/data/org.sshtunnel/iptables_g1 -t nat -D OUTPUT -p udp "
						+ "--dport 53 -j REDIRECT --to-ports 8153";
				runRootCommand(cmd);
			} else {
				String cmd = "/data/data/org.sshtunnel/iptables_n1 -t nat -D OUTPUT -p tcp "
						+ "--dport 80 -j REDIRECT --to-ports 8123\n"
						+ "/data/data/org.sshtunnel/iptables_n1 -t nat -D OUTPUT -p tcp "
						+ "--dport 443 -j REDIRECT --to-ports 8124\n"
						+ "/data/data/org.sshtunnel/iptables_n1 -t nat -D OUTPUT -p udp "
						+ "--dport 53 -j REDIRECT --to-ports 8153";
				runRootCommand(cmd);
			}

			if (isARMv6()) {
				String cmd = "/data/data/org.sshtunnel/iptables_g1 -t nat -A OUTPUT -p tcp "
						+ "--dport 80 -j REDIRECT --to-ports 8123\n"
						+ "/data/data/org.sshtunnel/iptables_g1 -t nat -A OUTPUT -p tcp "
						+ "--dport 443 -j REDIRECT --to-ports 8124\n"
						+ "/data/data/org.sshtunnel/iptables_g1 -t nat -A OUTPUT -p udp "
						+ "--dport 53 -j REDIRECT --to-ports 8153";
				runRootCommand(cmd);
			} else {
				String cmd = "/data/data/org.sshtunnel/iptables_n1 -t nat -A OUTPUT -p tcp "
						+ "--dport 80 -j REDIRECT --to-ports 8123\n"
						+ "/data/data/org.sshtunnel/iptables_n1 -t nat -A OUTPUT -p tcp "
						+ "--dport 443 -j REDIRECT --to-ports 8124\n"
						+ "/data/data/org.sshtunnel/iptables_n1 -t nat -A OUTPUT -p udp "
						+ "--dport 53 -j REDIRECT --to-ports 8153";
				runRootCommand(cmd);
			}

			// Forward Successful
			return true;

		} else {

			// Forward Unsuccessful
			return false;
		}

	}

	/** Called when the activity is first created. */
	public boolean handleCommand(Intent it) {

		Log.e(TAG, "Service Start");

		Bundle bundle = it.getExtras();
		host = bundle.getString("host");
		user = bundle.getString("user");
		password = bundle.getString("password");
		port = bundle.getInt("port");
		localPort = bundle.getInt("localPort");
		remotePort = bundle.getInt("remotePort");
		isAutoReconnect = bundle.getBoolean("isAutoReconnect");
		isAutoSetProxy = bundle.getBoolean("isAutoSetProxy");

		dnsServer = new DNSServer("DNS Server", 8153, "208.67.222.222", 5353);
//		dnsServer = new DNSServer("DNS Server", 8153, "127.0.0.1", 5353);
		dnsServer.setBasePath("/data/data/org.sshtunnel");
		new Thread(dnsServer).start();

		return connect();
	}

	private void notifyAlert(String title, String info) {
		notification.icon = R.drawable.icon;
		notification.tickerText = title;
		notification.flags = Notification.FLAG_ONGOING_EVENT;
		notification.defaults = Notification.DEFAULT_SOUND;
		notification.setLatestEventInfo(this, getString(R.string.app_name),
				info, pendIntent);
		notificationManager.notify(0, notification);
	}

	private void notifyAlert(String title, String info, int flags) {
		notification.icon = R.drawable.icon;
		notification.tickerText = title;
		notification.flags = flags;
		notification.setLatestEventInfo(this, getString(R.string.app_name),
				info, pendIntent);
		notificationManager.notify(0, notification);
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		settings = PreferenceManager.getDefaultSharedPreferences(this);
		notificationManager = (NotificationManager) this
				.getSystemService(NOTIFICATION_SERVICE);

		intent = new Intent(this, SSHTunnel.class);
		pendIntent = PendingIntent.getActivity(this, 0, intent, 0);
		notification = new Notification();
	}

	/** Called when the activity is closed. */
	@Override
	public void onDestroy() {

		if (connected) {

			notifyAlert(getString(R.string.forward_stop),
					getString(R.string.service_stopped),
					Notification.FLAG_AUTO_CANCEL);
		}

		// Make sure the connection is closed, important here
		onDisconnect();

		try {
			if (dnsServer != null)
				dnsServer.close();
		} catch (Exception e) {
			Log.e(TAG, "DNS Server close unexpected");
		}

		Editor ed = settings.edit();
		ed.putBoolean("isRunning", false);
		ed.commit();

		super.onDestroy();
	}

	private synchronized void onDisconnect() {

		connected = false;

		try {
			if (lpf1 != null) {
				lpf1.close();
				lpf1 = null;
			}
//			if (lpf2 != null) {
//				lpf2.close();
//				lpf2 = null;
//			}
		} catch (Exception ignore) {
			// Nothing
		}

		if (connection != null) {
			connection.close();
			connection = null;
		}

		if (isAutoSetProxy) {
            if (isARMv6()) {
                String cmd = "/data/data/org.sshtunnel/iptables_g1 -t nat -D OUTPUT -p tcp "
                                + "--dport 80 -j REDIRECT --to-ports 8123\n"
                                + "/data/data/org.sshtunnel/iptables_g1 -t nat -D OUTPUT -p tcp "
                                + "--dport 443 -j REDIRECT --to-ports 8124\n"
                                + "/data/data/org.sshtunnel/iptables_g1 -t nat -D OUTPUT -p udp "
                                + "--dport 53 -j REDIRECT --to-ports 8153";
                runRootCommand(cmd);
        } else {
                String cmd = "/data/data/org.sshtunnel/iptables_n1 -t nat -D OUTPUT -p tcp "
                                + "--dport 80 -j REDIRECT --to-ports 8123\n"
                                + "/data/data/org.sshtunnel/iptables_n1 -t nat -D OUTPUT -p tcp "
                                + "--dport 443 -j REDIRECT --to-ports 8124\n"
                                + "/data/data/org.sshtunnel/iptables_n1 -t nat -D OUTPUT -p udp "
                                + "--dport 53 -j REDIRECT --to-ports 8153";
                runRootCommand(cmd);
        }

			runRootCommand("/data/data/org.sshtunnel/proxy.sh stop");
		}

	}

	// This is the old onStart method that will be called on the pre-2.0
	// platform. On 2.0 or later we override onStartCommand() so this
	// method will not be called.
	@Override
	public void onStart(Intent intent, int startId) {
		if (isOnline() && handleCommand(intent)) {
			// Connection and forward successful
			notifyAlert(getString(R.string.forward_success),
					getString(R.string.service_running));

			Editor ed = settings.edit();
			ed.putBoolean("isRunning", true);
			ed.commit();
			super.onStart(intent, startId);

		} else {
			// Connection or forward unsuccessful
			notifyAlert(getString(R.string.forward_fail),
					getString(R.string.service_failed),
					Notification.FLAG_AUTO_CANCEL);
			connected = false;
			Editor ed = settings.edit();
			ed.putBoolean("isRunning", false);
			ed.commit();
			stopSelf();
		}
	}

	public boolean isOnline() {

		ConnectivityManager manager = (ConnectivityManager) this
				.getSystemService(CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = manager.getActiveNetworkInfo();
		if (networkInfo == null)
			return false;
		return true;
	}

}
