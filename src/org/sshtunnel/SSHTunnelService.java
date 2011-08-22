/* sshtunnel - SSH Tunnel App for Android
 * Copyright (C) 2011 Max Lv <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 *                            ___====-_  _-====___
 *                      _--^^^#####//      \\#####^^^--_
 *                   _-^##########// (    ) \\##########^-_
 *                  -############//  |\^^/|  \\############-
 *                _/############//   (@::@)   \\############\_
 *               /#############((     \\//     ))#############\
 *              -###############\\    (oo)    //###############-
 *             -#################\\  / VV \  //#################-
 *            -###################\\/      \//###################-
 *           _#/|##########/\######(   /\   )######/\##########|\#_
 *           |/ |#/\#/\#/\/  \#/\##\  |  |  /##/\#/  \/\#/\#/\#| \|
 *           `  |/  V  V  `   V  \#\| |  | |/#/  V   '  V  V  \|  '
 *              `   `  `      `   / | |  | | \   '      '  '   '
 *                               (  | |  | |  )
 *                              __\ | |  | | /__
 *                             (vvv(VVV)(VVV)vvv)
 *
 *                              HERE BE DRAGONS
 *
 */

package org.sshtunnel;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.sshtunnel.db.Profile;
import org.sshtunnel.db.ProfileFactory;
import org.sshtunnel.utils.Constraints;
import org.sshtunnel.utils.ProxyedApp;
import org.sshtunnel.utils.Utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import com.flurry.android.FlurryAgent;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionMonitor;
import com.trilead.ssh2.DynamicPortForwarder;
import com.trilead.ssh2.InteractiveCallback;
import com.trilead.ssh2.LocalPortForwarder;

public class SSHTunnelService extends Service implements InteractiveCallback,
		ConnectionMonitor {

	// ConnectivityBroadcastReceiver stateChanged = null;

	private Notification notification;
	private NotificationManager notificationManager;
	private Intent intent;
	private PendingIntent pendIntent;

	private static final String TAG = "SSHTunnel";
	private static final int MSG_CONNECT_START = 0;
	private static final int MSG_CONNECT_FINISH = 1;
	private static final int MSG_CONNECT_SUCCESS = 2;
	private static final int MSG_CONNECT_FAIL = 3;
	private static final int MSG_DISCONNECT_FINISH = 4;

	private SharedPreferences settings = null;
	
	private Profile profile;

	private String hostAddress = null;

	private boolean enableDNSProxy = true;
	
	private LocalPortForwarder lpf = null;
	private LocalPortForwarder dnspf = null;
	private DynamicPortForwarder dpf = null;
	private DNSServer dnsServer = null;
	private int dnsPort = 0;
	public volatile static boolean isConnecting = false;
	public volatile static boolean isStopping = false;

	private final static int AUTH_TRIES = 1;
	private final static int RECONNECT_TRIES = 2;

	private Connection connection;

	private volatile boolean connected = false;

	private ProxyedApp apps[] = null;

	public final static String BASE = "/data/data/org.sshtunnel/";

	final static String CMD_IPTABLES_REDIRECT_ADD = BASE
			+ "iptables -t nat -A SSHTUNNEL -p tcp --dport 80 -j REDIRECT --to 8123\n"
			+ BASE
			+ "iptables -t nat -A SSHTUNNEL -p tcp --dport 443 -j REDIRECT --to 8124\n";

	final static String CMD_IPTABLES_DNAT_ADD = BASE
			+ "iptables -t nat -A SSHTUNNEL -p tcp --dport 80 -j DNAT --to-destination 127.0.0.1:8123\n"
			+ BASE
			+ "iptables -t nat -A SSHTUNNEL -p tcp --dport 443 -j DNAT --to-destination 127.0.0.1:8124\n";

	final static String CMD_IPTABLES_REDIRECT_ADD_SOCKS = BASE
			+ "iptables -t nat -A SSHTUNNEL -p tcp --dport 5228 -j REDIRECT --to 8123\n";

	final static String CMD_IPTABLES_DNAT_ADD_SOCKS = BASE
			+ "iptables -t nat -A SSHTUNNEL -p tcp --dport 5228 -j DNAT --to-destination 127.0.0.1:8123\n";

	public static boolean runRootCommand(String command) {
		Process process = null;
		DataOutputStream os = null;

		Log.d(TAG, command);

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

	// Flag indicating if this is an ARMv6 device (-1: unknown, 0: no, 1: yes)
	private boolean hasRedirectSupport = true;
	private String reason = "";

	private static final Class<?>[] mStartForegroundSignature = new Class[] {
			int.class, Notification.class };

	private static final Class<?>[] mStopForegroundSignature = new Class[] { boolean.class };
	private Method mStartForeground;

	private Method mStopForeground;

	private Object[] mStartForegroundArgs = new Object[2];

	private Object[] mStopForegroundArgs = new Object[1];

	final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Editor ed = settings.edit();
			switch (msg.what) {
			case MSG_CONNECT_START:
				ed.putBoolean("isConnecting", true);
				break;
			case MSG_CONNECT_FINISH:
				ed.putBoolean("isConnecting", false);
				ed.putBoolean("isSwitching", false);
				break;
			case MSG_CONNECT_SUCCESS:
				ed.putBoolean("isRunning", true);
				// stateChanged = new ConnectivityBroadcastReceiver();
				// registerReceiver(stateChanged, new IntentFilter(
				// ConnectivityManager.CONNECTIVITY_ACTION));
				break;
			case MSG_CONNECT_FAIL:
				ed.putBoolean("isRunning", false);
				break;
			case MSG_DISCONNECT_FINISH:

				ed.putBoolean("isRunning", false);
				ed.putBoolean("isSwitching", false);

				try {
					notificationManager.cancel(0);
					notificationManager.cancel(1);
				} catch (Exception ignore) {
					// Nothing
				}

				// for widget, maybe exception here
				try {
					RemoteViews views = new RemoteViews(getPackageName(),
							R.layout.sshtunnel_appwidget);
					views.setImageViewResource(R.id.serviceToggle,
							R.drawable.off);
					AppWidgetManager awm = AppWidgetManager
							.getInstance(SSHTunnelService.this);
					awm.updateAppWidget(awm.getAppWidgetIds(new ComponentName(
							SSHTunnelService.this,
							SSHTunnelWidgetProvider.class)), views);
				} catch (Exception ignore) {
					// Nothing
				}
				break;
			}
			ed.commit();
			super.handleMessage(msg);
		}
	};

	private void authenticate() {
		try {
			if (connection.authenticateWithNone(profile.getUser())) {
				Log.d(TAG, "Authenticate with none");
				return;
			}
		} catch (Exception e) {
			Log.d(TAG, "Host does not support 'none' authentication.");
		}

		try {
			File f = new File(profile.getKeyPath());
			if (f.exists())
				if (profile.getPassword().equals(""))
					profile.setPassword(null);
			if (connection.authenticateWithPublicKey(profile.getUser(), f, profile.getPassword())) {
				Log.d(TAG, "Authenticate with public key");
				return;
			}
		} catch (Exception e) {
			Log.d(TAG, "Host does not support 'Public key' authentication.");
		}

		try {
			if (connection.authenticateWithPassword(profile.getUser(), profile.getPassword())) {
				Log.d(TAG, "Authenticate with password");
				return;
			}
		} catch (IllegalStateException e) {
			Log.e(TAG,
					"Connection went away while we were trying to authenticate",
					e);
		} catch (Exception e) {
			Log.e(TAG, "Problem during handleAuthentication()", e);
		}

		try {
			if (connection.authenticateWithKeyboardInteractive(profile.getUser(), this))
				return;
		} catch (Exception e) {
			Log.d(TAG,
					"Host does not support 'Keyboard-Interactive' authentication.");
		}
	}

	public boolean connect() {

		// try {
		//
		// connection.setCompression(true);
		// connection.setTCPNoDelay(true);
		//
		// } catch (IOException e) {
		// Log.e(TAG, "Could not enable compression!", e);
		// }

		try {
			connection = new Connection(profile.getHost(), profile.getPort());
			connection.addConnectionMonitor(this);

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

			connection.connect(null, 10 * 1000, 20 * 1000);
			connected = true;

		} catch (Exception e) {
			Log.e(TAG, "Problem in SSH connection thread during connecting", e);

			// Display the reason in the text.

			reason = getString(R.string.fail_to_connect);

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

			reason = getString(R.string.fail_to_authenticate);
			return false;
		}

		try {
			if (connection.isAuthenticationComplete()) {
				return enablePortForward();
			}
		} catch (Exception e) {
			Log.e(TAG, "Problem in SSH connection thread during enabling port",
					e);

			reason = getString(R.string.fail_to_connect);
			return false;
		}

		reason = getString(R.string.fail_to_authenticate);
		Log.e(TAG, "Cannot authenticate");
		return false;
	}

	@Override
	public void connectionLost(Throwable reason) {

		Log.d(TAG, "Connection Lost");

		SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");

		if (isConnecting || isStopping) {
			return;
		}

		if (!isOnline()) {
			stopReconnect(df);
			return;
		}

		if (reason != null) {
			if (reason.getMessage().contains(
					"There was a problem during connect")) {
				Log.e(TAG, "connection lost", reason);
				return;
			} else if (reason.getMessage().contains(
					"Closed due to user request")) {
				Log.e(TAG, "connection lost", reason);
				return;
			} else if (reason.getMessage().contains(
					"The connect timeout expired")) {
				stopReconnect(df);
				return;
			}
			Log.e(TAG, "connection lost: " + reason.getMessage());
		} else {
			stopReconnect(df);
			return;
		}

		if (profile.isAutoReconnect() && connected) {

			for (int reconNum = 1; reconNum <= RECONNECT_TRIES; reconNum++) {

				Log.d(TAG, "Reconnect tries: " + reconNum);

				onDisconnect();

				if (!connect()) {

					try {
						Thread.sleep(2000 * reconNum);
					} catch (Exception ignore) {
						// Nothing
					}

					continue;
				}

				notifyAlert(
						getString(R.string.reconnect_success) + " "
								+ df.format(new Date()),
						getString(R.string.reconnect_success));
				return;
			}
		}

		stopReconnect(df);
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

			dnspf = connection.createLocalPortForwarder(8053, "www.google.com",
					80);

			if (profile.isSocks()) {
				dpf = connection.createDynamicPortForwarder(profile.getLocalPort());
			} else {
				lpf = connection.createLocalPortForwarder(profile.getLocalPort(),
						profile.getRemoteAddress(), profile.getRemotePort());
			}

		} catch (Exception e) {
			Log.e(TAG, "Could not create local port forward", e);
			reason = getString(R.string.fail_to_forward);
			return false;
		}

		return true;
	}

	/**
	 * Internal method to request actual PTY terminal once we've finished
	 * authentication. If called before authenticated, it will just fail.
	 */
	private void finishConnection() {

		Log.e(TAG, "Forward Successful");

		if (profile.isSocks())
			runRootCommand(BASE + "proxy_socks.sh start " + profile.getLocalPort());
		else
			runRootCommand(BASE + "proxy_http.sh start " + profile.getLocalPort());

		StringBuffer cmd = new StringBuffer();

		cmd.append(BASE + "iptables -t nat -N SSHTUNNEL\n");
		cmd.append(BASE + "iptables -t nat -F SSHTUNNEL\n");

		if (enableDNSProxy) {

			cmd.append(BASE + "iptables -t nat -N SSHTUNNELDNS\n");
			cmd.append(BASE + "iptables -t nat -F SSHTUNNELDNS\n");

			if (hasRedirectSupport)
				cmd.append(BASE
						+ "iptables "
						+ "-t nat -A SSHTUNNELDNS -p udp --dport 53 -j REDIRECT --to "
						+ dnsPort + "\n");
			else
				cmd.append(BASE
						+ "iptables "
						+ "-t nat -A SSHTUNNELDNS -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:"
						+ dnsPort + "\n");

			cmd.append(BASE
					+ "iptables -t nat -A OUTPUT -p udp -j SSHTUNNELDNS\n");
		}

		cmd.append(hasRedirectSupport ? CMD_IPTABLES_REDIRECT_ADD
				: CMD_IPTABLES_DNAT_ADD);

		if (profile.isSocks())
			cmd.append(hasRedirectSupport ? CMD_IPTABLES_REDIRECT_ADD_SOCKS
					: CMD_IPTABLES_DNAT_ADD_SOCKS);

		if (profile.isGFWList()) {
			String[] gfw_list = getResources().getStringArray(
					R.array.gfw_list);

			for (String item : gfw_list) {
				cmd.append(BASE + "iptables -t nat -A OUTPUT -p tcp -d "
						+ item + " -j SSHTUNNEL\n");
			}
		} else if (profile.isAutoSetProxy()) {
			cmd.append(BASE + "iptables -t nat -A OUTPUT -p tcp -j SSHTUNNEL\n");
		} else {

			// for proxy specified apps
			if (apps == null || apps.length <= 0)
				apps = AppManager.getProxyedApps(this, profile.getProxyedApps());

			for (int i = 0; i < apps.length; i++) {
				if (apps[i].isProxyed()) {
					cmd.append(BASE + "iptables "
							+ "-t nat -m owner --uid-owner " + apps[i].getUid()
							+ " -A OUTPUT -p tcp -j SSHTUNNEL\n");
				}
			}
		}

		String rules = cmd.toString();

		if (hostAddress != null)
			rules = rules.replace("--dport 443",
					"! -d " + hostAddress + " --dport 443").replace(
					"--dport 80", "! -d " + hostAddress + " --dport 80");

		if (profile.isSocks())
			runRootCommand(rules.replace("8124", "8123"));
		else
			runRootCommand(rules);

	}

	private void flushIptables() {

		StringBuffer cmd = new StringBuffer();

		cmd.append(BASE + "iptables -t nat -F SSHTUNNEL\n");
		cmd.append(BASE + "iptables -t nat -X SSHTUNNEL\n");

		if (enableDNSProxy) {
			cmd.append(BASE + "iptables -t nat -F SSHTUNNELDNS\n");
			cmd.append(BASE + "iptables -t nat -X SSHTUNNELDNS\n");
			cmd.append(BASE
					+ "iptables -t nat -D OUTPUT -p udp -j SSHTUNNELDNS\n");
		}

		if (profile.isGFWList()) {
			String[] gfw_list = getResources().getStringArray(
					R.array.gfw_list);

			for (String item : gfw_list) {
				cmd.append(BASE + "iptables -t nat -D OUTPUT -p tcp -d "
						+ item + " -j SSHTUNNEL\n");
			}
		} else if (profile.isAutoSetProxy()) {
			cmd.append(BASE + "iptables -t nat -D OUTPUT -p tcp -j SSHTUNNEL\n");
		} else {

			// for proxy specified apps
			if (apps == null || apps.length <= 0)
				apps = AppManager.getProxyedApps(this, profile.getProxyedApps());

			for (int i = 0; i < apps.length; i++) {
				if (apps[i].isProxyed()) {
					cmd.append(BASE + "iptables "
							+ "-t nat -m owner --uid-owner " + apps[i].getUid()
							+ " -D OUTPUT -p tcp -j SSHTUNNEL\n");
				}
			}
		}

		String rules = cmd.toString();

		runRootCommand(rules);

		if (profile.isSocks())
			runRootCommand(BASE + "proxy_socks.sh stop");
		else
			runRootCommand(BASE + "proxy_http.sh stop");

	}

	private void initHasRedirectSupported() {
		Process process = null;
		DataOutputStream os = null;
		DataInputStream es = null;

		String command;
		String line = null;

		command = BASE
				+ "iptables -t nat -A OUTPUT -p udp --dport 54 -j REDIRECT --to 8154";

		try {
			process = Runtime.getRuntime().exec("su");
			es = new DataInputStream(process.getErrorStream());
			os = new DataOutputStream(process.getOutputStream());
			os.writeBytes(command + "\n");
			os.writeBytes("exit\n");
			os.flush();
			process.waitFor();

			while (null != (line = es.readLine())) {
				Log.d(TAG, line);
				if (line.contains("No chain/target/match")) {
					this.hasRedirectSupport = false;
					break;
				}
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		} finally {
			try {
				if (os != null) {
					os.close();
				}
				if (es != null)
					es.close();
				process.destroy();
			} catch (Exception e) {
				// nothing
			}
		}

		// flush the check command
		runRootCommand(command.replace("-A", "-D"));
	}

	private void initSoundVibrateLights(Notification notification) {
		final String ringtone = settings.getString(
				"settings_key_notif_ringtone", null);
		AudioManager audioManager = (AudioManager) this
				.getSystemService(Context.AUDIO_SERVICE);
		if (audioManager.getStreamVolume(AudioManager.STREAM_RING) == 0) {
			notification.sound = null;
		} else if (ringtone != null)
			notification.sound = Uri.parse(ringtone);
		else
			notification.defaults |= Notification.DEFAULT_SOUND;

		if (settings.getBoolean("settings_key_notif_icon", true)){
			notification.icon = R.drawable.ic_stat;
		} else {
			notification.icon = R.drawable.ic_stat_trans;
		}
		
		if (settings.getBoolean("settings_key_notif_vibrate", false)) {
			long[] vibrate = { 0, 1000, 500, 1000, 500, 1000 };
			notification.vibrate = vibrate;
		}

		notification.defaults |= Notification.DEFAULT_LIGHTS;
	}

	void invokeMethod(Method method, Object[] args) {
		try {
			method.invoke(this, mStartForegroundArgs);
		} catch (InvocationTargetException e) {
			// Should not happen.
			Log.w("ApiDemos", "Unable to invoke method", e);
		} catch (IllegalAccessException e) {
			// Should not happen.
			Log.w("ApiDemos", "Unable to invoke method", e);
		}
	}

	public boolean isOnline() {

		ConnectivityManager manager = (ConnectivityManager) this
				.getSystemService(CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = manager.getActiveNetworkInfo();
		if (networkInfo == null) {
			reason = getString(R.string.fail_to_online);
			return false;
		}
		return true;
	}

	private void notifyAlert(String title, String info) {
		notification.tickerText = title;
		notification.flags = Notification.FLAG_ONGOING_EVENT;
		// notification.defaults = Notification.DEFAULT_SOUND;
		initSoundVibrateLights(notification);
		notification.setLatestEventInfo(this, getString(R.string.app_name)
				+ " | " + Utils.getProfileName(profile, this), info, pendIntent);
		notificationManager.cancel(1);
		startForegroundCompat(1, notification);
	}

	private void notifyAlert(String title, String info, int flags) {
		notification.tickerText = title;
		notification.flags = flags;
		initSoundVibrateLights(notification);
		notification.setLatestEventInfo(this, getString(R.string.app_name)
				+ " | " + Utils.getProfileName(profile, this), info, pendIntent);
		notificationManager.cancel(0);
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
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		pendIntent = PendingIntent.getActivity(this, 0, intent, 0);
		notification = new Notification();

		try {
			mStartForeground = getClass().getMethod("startForeground",
					mStartForegroundSignature);
			mStopForeground = getClass().getMethod("stopForeground",
					mStopForegroundSignature);
		} catch (NoSuchMethodException e) {
			// Running on an older platform.
			mStartForeground = mStopForeground = null;
		}

		reason = getString(R.string.fail_to_connect);
	}

	/** Called when the activity is closed. */
	@Override
	public void onDestroy() {

		isStopping = true;

		stopForegroundCompat(1);
		
		FlurryAgent.onEndSession(this);

		// if (stateChanged != null) {
		// unregisterReceiver(stateChanged);
		// stateChanged = null;
		// }

		if (connected) {

			notifyAlert(getString(R.string.forward_stop),
					getString(R.string.service_stopped),
					Notification.FLAG_AUTO_CANCEL);
		}

		if (enableDNSProxy) {
			try {
				if (dnsServer != null) {
					dnsServer.close();
					dnsServer = null;
				}
			} catch (Exception e) {
				Log.e(TAG, "DNS Server close unexpected");
			}
		}

		new Thread() {
			@Override
			public void run() {

				// Make sure the connection is closed, important here
				onDisconnect();

				isStopping = false;

				handler.sendEmptyMessage(MSG_DISCONNECT_FINISH);
			}
		}.start();

		flushIptables();

		super.onDestroy();
	}

	private void onDisconnect() {
		connected = false;

		try {
			if (lpf != null) {
				lpf.close();
				lpf = null;
			}
		} catch (IOException ignore) {
			// Nothing
		}
		try {
			if (dpf != null) {
				dpf.close();
				dpf = null;
			}
		} catch (IOException ignore) {
			// Nothing
		}
		try {
			if (dnspf != null) {
				dnspf.close();
				dnspf = null;
			}
		} catch (IOException ignore) {
			// Nothing
		}

		if (connection != null) {
			connection.close();
			connection = null;
		}

	}

	// This is the old onStart method that will be called on the pre-2.0
	// platform. On 2.0 or later we override onStartCommand() so this
	// method will not be called.
	@Override
	public void onStart(Intent intent, int startId) {

		super.onStart(intent, startId);
		
		FlurryAgent.onStartSession(this, "MBY4JL18FQK1DPEJ5Y39");

		Log.d(TAG, "Service Start");

		Bundle bundle = intent.getExtras();
		int id = bundle.getInt(Constraints.ID);
		profile = ProfileFactory.loadProfileFromDao(this, id);
		
		Log.d(TAG, profile.toString());

		new Thread(new Runnable() {
			@Override
			public void run() {

				handler.sendEmptyMessage(MSG_CONNECT_START);
				isConnecting = true;

				try {
					URL url = new URL("http://gae-ip-country.appspot.com/");
					HttpURLConnection conn = (HttpURLConnection) url
							.openConnection();
					conn.setConnectTimeout(2000);
					conn.setReadTimeout(5000);
					conn.connect();
					InputStream is = conn.getInputStream();
					BufferedReader input = new BufferedReader(
							new InputStreamReader(is));
					String code = input.readLine();
					if (code != null && code.length() > 0 && code.length() < 3) {
						Log.d(TAG, "Location: " + code);
						if (!code.contains("CN") && !code.contains("ZZ"))
							enableDNSProxy = false;
					}
				} catch (Exception e) {
					Log.d(TAG, "Cannot get country code");
					enableDNSProxy = true;
					// Nothing
				}

				if (enableDNSProxy) {
					if (dnsServer == null) {
						// dnsServer = new DNSServer("DNS Server", "8.8.4.4",
						// 53,
						// SSHTunnelService.this);
						dnsServer = new DNSServer("DNS Server", "127.0.0.1",
								8053, SSHTunnelService.this);
						dnsServer.setBasePath("/data/data/org.sshtunnel");
						dnsPort = dnsServer.init();
					}
				}

				try {
					hostAddress = InetAddress.getByName(profile.getHost()).getHostAddress();
				} catch (UnknownHostException e) {
					hostAddress = null;
				}

				// Test for Redirect Support
				initHasRedirectSupported();

				if (isOnline() && hostAddress != null && connect()) {

					isConnecting = false;

					// Connection and forward successful
					finishConnection();

					if (enableDNSProxy) {
						// Start DNS Proxy

						Thread dnsThread = new Thread(dnsServer);
						dnsThread.setDaemon(true);
						dnsThread.start();
					}

					notifyAlert(getString(R.string.forward_success),
							getString(R.string.service_running));
					handler.sendEmptyMessage(MSG_CONNECT_FINISH);
					handler.sendEmptyMessage(MSG_CONNECT_SUCCESS);

					// for widget, maybe exception here
					try {
						RemoteViews views = new RemoteViews(getPackageName(),
								R.layout.sshtunnel_appwidget);
						views.setImageViewResource(R.id.serviceToggle,
								R.drawable.on);
						AppWidgetManager awm = AppWidgetManager
								.getInstance(SSHTunnelService.this);
						awm.updateAppWidget(awm
								.getAppWidgetIds(new ComponentName(
										SSHTunnelService.this,
										SSHTunnelWidgetProvider.class)), views);
					} catch (Exception ignore) {
						// Nothing
					}

				} else {
					// Connection or forward unsuccessful
					notifyAlert(getString(R.string.forward_fail) + ": "
							+ reason, getString(R.string.service_failed),
							Notification.FLAG_AUTO_CANCEL);

					handler.sendEmptyMessage(MSG_CONNECT_FINISH);
					handler.sendEmptyMessage(MSG_CONNECT_FAIL);

					try {
						Thread.sleep(1000);
					} catch (InterruptedException ignore) {
						// Nothing
					}

					connected = false;
					stopSelf();
				}
			}
		}).start();
	}

	@Override
	public String[] replyToChallenge(String name, String instruction,
			int numPrompts, String[] prompt, boolean[] echo) throws Exception {
		String[] responses = new String[numPrompts];
		for (int i = 0; i < numPrompts; i++) {
			// request response from user for each prompt
			if (prompt[i].toLowerCase().contains("password"))
				responses[i] = profile.getPassword();
		}
		return responses;
	}

	/**
	 * This is a wrapper around the new startForeground method, using the older
	 * APIs if it is not available.
	 */
	void startForegroundCompat(int id, Notification notification) {
		// If we have the new startForeground API, then use it.
		if (mStartForeground != null) {
			mStartForegroundArgs[0] = Integer.valueOf(id);
			mStartForegroundArgs[1] = notification;
			invokeMethod(mStartForeground, mStartForegroundArgs);
			return;
		}

		// Fall back on the old API.
		setForeground(true);
		notificationManager.notify(id, notification);
	}

	/**
	 * This is a wrapper around the new stopForeground method, using the older
	 * APIs if it is not available.
	 */
	void stopForegroundCompat(int id) {
		// If we have the new stopForeground API, then use it.
		if (mStopForeground != null) {
			mStopForegroundArgs[0] = Boolean.TRUE;
			try {
				mStopForeground.invoke(this, mStopForegroundArgs);
			} catch (InvocationTargetException e) {
				// Should not happen.
				Log.w("ApiDemos", "Unable to invoke stopForeground", e);
			} catch (IllegalAccessException e) {
				// Should not happen.
				Log.w("ApiDemos", "Unable to invoke stopForeground", e);
			}
			return;
		}

		// Fall back on the old API. Note to cancel BEFORE changing the
		// foreground state, since we could be killed at that point.
		notificationManager.cancel(id);
		setForeground(false);
	}

	public void stopReconnect(SimpleDateFormat df) {
		connected = false;
		notifyAlert(
				getString(R.string.reconnect_fail) + " "
						+ df.format(new Date()),
				getString(R.string.reconnect_fail),
				Notification.FLAG_AUTO_CANCEL);
		stopSelf();
	}
}
