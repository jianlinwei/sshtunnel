package org.sshtunnel;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class SSHTunnelService extends Service implements ConnectionMonitor {

	private NotificationManager notificationManager;
	private Intent intent;
	private PendingIntent pendIntent;
	private SSHMonitor sm;

	private static final String TAG = "SSHTunnel";
	private SharedPreferences settings = null;

	private Process sshProcess = null;
	private DataOutputStream sshOS = null;

//	private Process proxyProcess = null;
//	private DataOutputStream proxyOS = null;

	private String host;
	private String hostIP = "127.0.0.1";
	private int port;
	private int localPort;
	private int remotePort;
	private String user;
	private String password;
	private boolean isAutoSetProxy = false;
	// private LocalPortForwarder lpf2 = null;
	private DNSServer dnsServer = null;

	private boolean connected = false;

	final static String CMD_IPTABLES_DNAT_DEL_G1 = "/data/data/org.sshtunnel/iptables_g1 -t nat -D OUTPUT -p tcp --dport 80 -j DNAT --to-destination 127.0.0.1:8123\n"
			+ "/data/data/org.sshtunnel/iptables_g1 -t nat -D OUTPUT -p tcp --dport 443 -j DNAT --to-destination 127.0.0.1:8124\n"
			+ "/data/data/org.sshtunnel/iptables_g1 -t nat -D OUTPUT -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:8153";
	final static String CMD_IPTABLES_DNAT_ADD_G1 = "/data/data/org.sshtunnel/iptables_g1 -t nat -A OUTPUT -p tcp --dport 80 -j DNAT --to-destination 127.0.0.1:8123\n"
			+ "/data/data/org.sshtunnel/iptables_g1 -t nat -A OUTPUT -p tcp --dport 443 -j DNAT --to-destination 127.0.0.1:8124\n"
			+ "/data/data/org.sshtunnel/iptables_g1 -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:8153";

	final static String CMD_IPTABLES_DNAT_DEL_N1 = "/data/data/org.sshtunnel/iptables_n1 -t nat -D OUTPUT -p tcp --dport 80 -j DNAT --to-destination 127.0.0.1:8123\n"
			+ "/data/data/org.sshtunnel/iptables_n1 -t nat -D OUTPUT -p tcp --dport 443 -j DNAT --to-destination 127.0.0.1:8124\n"
			+ "/data/data/org.sshtunnel/iptables_n1 -t nat -D OUTPUT -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:8153";

	final static String CMD_IPTABLES_DNAT_ADD_N1 = "/data/data/org.sshtunnel/iptables_n1 -t nat -A OUTPUT -p tcp --dport 80 -j DNAT --to-destination 127.0.0.1:8123\n"
			+ "/data/data/org.sshtunnel/iptables_n1 -t nat -A OUTPUT -p tcp --dport 443 -j DNAT --to-destination 127.0.0.1:8124\n"
			+ "/data/data/org.sshtunnel/iptables_n1 -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:8153";

	// Flag indicating if this is an ARMv6 device (-1: unknown, 0: no, 1: yes)
	public static int isARMv6 = -1;
	private boolean hasRedirectSupport = true;

	public static final String BASE = "/data/data/org.sshtunnel/";

	private static final Class<?>[] mStartForegroundSignature = new Class[] {
			int.class, Notification.class };
	private static final Class<?>[] mStopForegroundSignature = new Class[] { boolean.class };

	private Method mStartForeground;
	private Method mStopForeground;

	private Object[] mStartForegroundArgs = new Object[2];
	private Object[] mStopForegroundArgs = new Object[1];

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

	/**
	 * Check if this is an ARMv6 device
	 * 
	 * @return true if this is ARMv6
	 */
	public static boolean isARMv6() {
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
				if (r != null) {
					try {
						r.close();
					} catch (Exception ex) {
					}
				}
			}

		}
		Log.d(TAG, "isARMv6: " + isARMv6);
		return (isARMv6 == 1);
	}

	private void initHasRedirectSupported() {
		Process process = null;
		DataOutputStream os = null;
		DataInputStream es = null;

		String command;
		String line = null;

		if (isARMv6()) {
			command = "/data/data/org.sshtunnel/iptables_g1 -t nat -A OUTPUT -p udp --dport 53 -j REDIRECT --to-ports 8153";
		} else
			command = "/data/data/org.sshtunnel/iptables_n1 -t nat -A OUTPUT -p udp --dport 53 -j REDIRECT --to-ports 8153";

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

	public boolean connect() {

		String cmd = "";
//		
//		if (SSHTunnelService.isARMv6())
//			cmd = "/data/data/org.sshtunnel/ssh_g1 -y " + user + "@" + host
//					+ "/" + port;
//		else
//			cmd = "/data/data/org.sshtunnel/ssh_n1 -y " + user + "@" + host
//					+ "/" + port;
//		
//		try {
//
//			String cmd1 = cmd
//					+ " wget http://sshtunnel.googlecode.com/files/run.sh";
//
//			Process p = Runtime.getRuntime().exec(cmd1);
//			DataOutputStream os = new DataOutputStream(p.getOutputStream());
//
//			Log.e(TAG, cmd1);
//
//			os.writeBytes(password + "\n");
//			os.flush();
//
//			p.waitFor();
//			p.destroy();
//			os.close();
//
//		} catch (Exception e) {
//			Log.e(TAG, e.getMessage());
//		}
//		
//		try {
//			String cmd2 = cmd + " bash ./run.sh";
//			proxyProcess = Runtime.getRuntime().exec(cmd2);
//
//			proxyOS = new DataOutputStream(proxyProcess.getOutputStream());
//
//			Log.e(TAG, cmd2);
//
//			proxyOS.writeBytes(password + "\n");
//			proxyOS.flush();
//
//		} catch (Exception e) {
//			Log.e(TAG, e.getMessage());
//		}
		
		try {
			cmd = "";
			if (isARMv6())
				cmd = "/data/data/org.sshtunnel/ssh_g1 -N -T -y -L "
						+ localPort + ":" + "127.0.0.1" + ":" + remotePort
						+ " -L " + "5353:8.8.8.8:53 " + user + "@" + hostIP
						+ "/" + port;
			else
				cmd = "/data/data/org.sshtunnel/ssh_n1 -N -T -y -L "
						+ localPort + ":" + "127.0.0.1" + ":" + remotePort
						+ " -L " + "5353:8.8.8.8:53 " + user + "@" + hostIP
						+ "/" + port;

			Log.e(TAG, cmd);

			sshProcess = Runtime.getRuntime().exec(cmd);
			sshOS = new DataOutputStream(sshProcess.getOutputStream());
			sshOS.writeBytes(password + "\n");
			sshOS.flush();

		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
			return false;
		}


		finishConnection();
		return true;
	}

	/**
	 * Internal method to request actual PTY terminal once we've finished
	 * authentication. If called before authenticated, it will just fail.
	 */
	private void finishConnection() {

		Log.e(TAG, "Forward Successful");
		if (isAutoSetProxy) {
			runRootCommand("/data/data/org.sshtunnel/proxy.sh start "
					+ localPort);

			// XXX: Flush iptables first?
			if (isARMv6()) {
				String cmd = "/data/data/org.sshtunnel/iptables_g1 -t nat -D OUTPUT -p tcp "
						+ "--dport 80 -j REDIRECT --to-ports 8123\n"
						+ "/data/data/org.sshtunnel/iptables_g1 -t nat -D OUTPUT -p tcp "
						+ "-d ! "
						+ hostIP
						+ " "
						+ "--dport 443 -j REDIRECT --to-ports 8124\n"
						+ "/data/data/org.sshtunnel/iptables_g1 -t nat -D OUTPUT -p udp "
						+ "--dport 53 -j REDIRECT --to-ports 8153";
				runRootCommand(hasRedirectSupport ? cmd
						: getCmd(CMD_IPTABLES_DNAT_DEL_G1));
			} else {
				String cmd = "/data/data/org.sshtunnel/iptables_n1 -t nat -D OUTPUT -p tcp "
						+ "--dport 80 -j REDIRECT --to-ports 8123\n"
						+ "/data/data/org.sshtunnel/iptables_n1 -t nat -D OUTPUT -p tcp "
						+ "-d ! "
						+ hostIP
						+ " "
						+ "--dport 443 -j REDIRECT --to-ports 8124\n"
						+ "/data/data/org.sshtunnel/iptables_n1 -t nat -D OUTPUT -p udp "
						+ "--dport 53 -j REDIRECT --to-ports 8153";
				runRootCommand(hasRedirectSupport ? cmd
						: getCmd(CMD_IPTABLES_DNAT_DEL_N1));
			}

			if (isARMv6()) {
				String cmd = "/data/data/org.sshtunnel/iptables_g1 -t nat -A OUTPUT -p tcp "
						+ "--dport 80 -j REDIRECT --to-ports 8123\n"
						+ "/data/data/org.sshtunnel/iptables_g1 -t nat -A OUTPUT -p tcp "
						+ "-d ! "
						+ hostIP
						+ " "
						+ "--dport 443 -j REDIRECT --to-ports 8124\n"
						+ "/data/data/org.sshtunnel/iptables_g1 -t nat -A OUTPUT -p udp "
						+ "--dport 53 -j REDIRECT --to-ports 8153";
				runRootCommand(hasRedirectSupport ? cmd
						: getCmd(CMD_IPTABLES_DNAT_ADD_G1));
			} else {
				String cmd = "/data/data/org.sshtunnel/iptables_n1 -t nat -A OUTPUT -p tcp "
						+ "--dport 80 -j REDIRECT --to-ports 8123\n"
						+ "/data/data/org.sshtunnel/iptables_n1 -t nat -A OUTPUT -p tcp "
						+ "-d ! "
						+ hostIP
						+ " "
						+ "--dport 443 -j REDIRECT --to-ports 8124\n"
						+ "/data/data/org.sshtunnel/iptables_n1 -t nat -A OUTPUT -p udp "
						+ "--dport 53 -j REDIRECT --to-ports 8153";
				runRootCommand(hasRedirectSupport ? cmd
						: getCmd(CMD_IPTABLES_DNAT_ADD_N1));
			}
		}

		// Forward Successful
		return;

	}

	private String getCmd(String cmd) {
		return cmd.replace("--dport 443", "! -d " + hostIP + " "
				+ "--dport 443");
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
		isAutoSetProxy = bundle.getBoolean("isAutoSetProxy");

		try {
			InetAddress ia;
			ia = InetAddress.getByName(host);
			String ip = ia.getHostAddress();
			if (ip != null && !ip.equals(""))
				hostIP = ip;
		} catch (UnknownHostException e) {
			Log.e(TAG, "cannot resolve the host name");
			return false;
		}

		Log.d(TAG, "Host IP: " + hostIP);

		// dnsServer = new DNSServer("DNS Server", 8153, "208.67.222.222",
		// 5353);
		dnsServer = new DNSServer("DNS Server", 8153, "127.0.0.1", 5353);
		dnsServer.setBasePath("/data/data/org.sshtunnel");
		new Thread(dnsServer).start();

		return connect();
	}

	private void notifyAlert(String title, String info) {
		Notification notification = new Notification(R.drawable.icon, title,
				System.currentTimeMillis());

		initSoundVibrateLights(notification);

		notification.setLatestEventInfo(this, getString(R.string.app_name),
				info, pendIntent);
		startForegroundCompat(1, notification);
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

		if (settings.getBoolean("settings_key_notif_vibrate", false)) {
			long[] vibrate = { 0, 1000, 500, 1000, 500, 1000 };
			notification.vibrate = vibrate;
		}

		notification.defaults |= Notification.DEFAULT_LIGHTS;
	}

	private void notifyAlert(String title, String info, int flags) {
		Notification notification = new Notification(R.drawable.icon, title,
				System.currentTimeMillis());
		notification.flags = flags;

		initSoundVibrateLights(notification);

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

		initHasRedirectSupported();

		intent = new Intent(this, SSHTunnel.class);
		pendIntent = PendingIntent.getActivity(this, 0, intent, 0);

		try {
			mStartForeground = getClass().getMethod("startForeground",
					mStartForegroundSignature);
			mStopForeground = getClass().getMethod("stopForeground",
					mStopForegroundSignature);
		} catch (NoSuchMethodException e) {
			// Running on an older platform.
			mStartForeground = mStopForeground = null;
		}
	}

	/** Called when the activity is closed. */
	@Override
	public void onDestroy() {

		stopForegroundCompat(1);

		synchronized (this) {
			if (sm != null) {
				sm.close();
				sm = null;
			}

			if (connected) {

				notifyAlert(getString(R.string.forward_stop),
						getString(R.string.service_stopped),
						Notification.FLAG_AUTO_CANCEL);
			}

			// Make sure the connection is closed, important here
			onDisconnect();
		}

		try {
			if (dnsServer != null) {
				dnsServer.close();
				dnsServer = null;
			}
		} catch (Exception e) {
			Log.e(TAG, "DNS Server close unexpected");
		}

		Editor ed = settings.edit();
		ed.putBoolean("isRunning", false);
		ed.commit();

		super.onDestroy();
	}

	private void onDisconnect() {

		connected = false;

		try {
			if (sshOS != null) {
				sshOS.close();
				sshOS = null;
			}
			if (sshProcess != null) {
				sshProcess.destroy();
				sshProcess = null;
			}
			
//			if (proxyOS != null) {
//				proxyOS.close();
//				proxyOS = null;
//			}
//			if (proxyProcess != null) {
//				proxyProcess.destroy();
//				proxyProcess = null;
//			}
		} catch (Exception e) {

			Log.e(TAG, "close connection error", e);
		}

		if (isAutoSetProxy) {
			if (isARMv6()) {
				String cmd = "/data/data/org.sshtunnel/iptables_g1 -t nat -D OUTPUT -p tcp "
						+ "--dport 80 -j REDIRECT --to-ports 8123\n"
						+ "/data/data/org.sshtunnel/iptables_g1 -t nat -D OUTPUT -p tcp "
						+ "-d ! "
						+ hostIP
						+ " "
						+ "--dport 443 -j REDIRECT --to-ports 8124\n"
						+ "/data/data/org.sshtunnel/iptables_g1 -t nat -D OUTPUT -p udp "
						+ "--dport 53 -j REDIRECT --to-ports 8153";
				runRootCommand(hasRedirectSupport ? cmd
						: getCmd(CMD_IPTABLES_DNAT_DEL_G1));
			} else {
				String cmd = "/data/data/org.sshtunnel/iptables_n1 -t nat -D OUTPUT -p tcp "
						+ "--dport 80 -j REDIRECT --to-ports 8123\n"
						+ "/data/data/org.sshtunnel/iptables_n1 -t nat -D OUTPUT -p tcp "
						+ "-d ! "
						+ hostIP
						+ " "
						+ "--dport 443 -j REDIRECT --to-ports 8124\n"
						+ "/data/data/org.sshtunnel/iptables_n1 -t nat -D OUTPUT -p udp "
						+ "--dport 53 -j REDIRECT --to-ports 8153";
				runRootCommand(hasRedirectSupport ? cmd
						: getCmd(CMD_IPTABLES_DNAT_DEL_N1));
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
			connected = true;
			Editor ed = settings.edit();
			ed.putBoolean("isRunning", true);
			ed.commit();
			sm = new SSHMonitor();
			sm.setMonitor(this);
			new Thread(sm).start();
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

	@Override
	public void connectionLost(boolean isReconnect) {

		if (!connected)
			return;

		SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");

		if (!isOnline() || !isReconnect) {

			connected = false;
			notifyAlert(
					getString(R.string.auto_reconnected) + " "
							+ df.format(new Date()),
					getString(R.string.reconnect_fail) + " @"
							+ df.format(new Date()),
					Notification.FLAG_AUTO_CANCEL);
			stopSelf();
			return;
		}

		try {
			Thread.sleep(1000);
		} catch (Exception ignore) {
			// Nothing
		}

		synchronized (this) {
			onDisconnect();

			connect();

			connected = true;
		}

		return;

	}

	@Override
	public void notifySuccess() {
		SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
		notifyAlert(
				getString(R.string.auto_reconnected) + " "
						+ df.format(new Date()),
				getString(R.string.reconnect_success) + " @"
						+ df.format(new Date()));
	}

	@Override
	public void waitFor() {
		try {
			if (sshProcess != null) {
				sshProcess.waitFor();
			}
		} catch (Exception ignore) {
			// Nothing
		}

	}

}
