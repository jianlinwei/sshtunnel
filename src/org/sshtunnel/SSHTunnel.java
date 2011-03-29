package org.sshtunnel;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

public class SSHTunnel extends PreferenceActivity implements
		OnSharedPreferenceChangeListener {

	private static final String TAG = "SSHTunnel";
	public static final String SERVICE_NAME = "org.sshtunnel.SSHTunnelService";

	private ProgressDialog pd = null;
	private String host;
	private int port;
	private int localPort;
	private int remotePort;
	private String user;
	private String password;
	public static boolean isAutoConnect = false;
	public static boolean isAutoReconnect = false;
	public static boolean isAutoSetProxy = false;
	public static boolean isSocks = false;
	public static boolean isRoot = false;

	private CheckBoxPreference isAutoConnectCheck;
	private CheckBoxPreference isAutoReconnectCheck;
	private CheckBoxPreference isAutoSetProxyCheck;
	private CheckBoxPreference isSocksCheck;

	private EditTextPreference hostText;
	private EditTextPreference portText;
	private EditTextPreference userText;
	private EditTextPreference passwordText;
	private EditTextPreference localPortText;
	private EditTextPreference remotePortText;
	private CheckBoxPreference isRunningCheck;
	private Preference proxyedApps;

	public static boolean runCommand(String command) {
		Process process = null;
		try {
			process = Runtime.getRuntime().exec(command);
			process.waitFor();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
			return false;
		} finally {
			try {
				process.destroy();
			} catch (Exception e) {
				// nothing
			}
		}
		return true;
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

	private void CopyAssets() {
		AssetManager assetManager = getAssets();
		String[] files = null;
		try {
			files = assetManager.list("");
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}
		for (int i = 0; i < files.length; i++) {
			InputStream in = null;
			OutputStream out = null;
			try {
				// if (!(new File("/data/data/org.sshtunnel/" +
				// files[i])).exists()) {
				in = assetManager.open(files[i]);
				out = new FileOutputStream("/data/data/org.sshtunnel/"
						+ files[i]);
				copyFile(in, out);
				in.close();
				in = null;
				out.flush();
				out.close();
				out = null;
				// }
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
		}
	}

	private void copyFile(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024];
		int read;
		while ((read = in.read(buffer)) != -1) {
			out.write(buffer, 0, read);
		}
	}

	private boolean isTextEmpty(String s, String msg) {
		if (s == null || s.length() <= 0) {
			showAToast(msg);
			return true;
		}
		return false;
	}

	public boolean isWorked(String service) {
		ActivityManager myManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		ArrayList<RunningServiceInfo> runningService = (ArrayList<RunningServiceInfo>) myManager
				.getRunningServices(30);
		for (int i = 0; i < runningService.size(); i++) {
			if (runningService.get(i).service.getClassName().toString()
					.equals(service)) {
				return true;
			}
		}
		return false;
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.main_pre);

		hostText = (EditTextPreference) findPreference("host");
		portText = (EditTextPreference) findPreference("port");
		userText = (EditTextPreference) findPreference("user");
		passwordText = (EditTextPreference) findPreference("password");
		localPortText = (EditTextPreference) findPreference("localPort");
		remotePortText = (EditTextPreference) findPreference("remotePort");
		proxyedApps = (Preference) findPreference("proxyedApps");

		isRunningCheck = (CheckBoxPreference) findPreference("isRunning");
		isAutoSetProxyCheck = (CheckBoxPreference) findPreference("isAutoSetProxy");
		isSocksCheck = (CheckBoxPreference) findPreference("isSocks");
		isAutoConnectCheck = (CheckBoxPreference) findPreference("isAutoConnect");
		isAutoReconnectCheck = (CheckBoxPreference) findPreference("isAutoReconnect");

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);

		Editor edit = settings.edit();

		if (this.isWorked(SERVICE_NAME)) {
			edit.putBoolean("isRunning", true);
		} else {
			if (settings.getBoolean("isRunning", false)) {
				showAToast(getString(R.string.crash_alert));
				recovery();
			}
			edit.putBoolean("isRunning", false);
		}

		edit.commit();

		if (settings.getBoolean("isRunning", false)) {
			isRunningCheck.setChecked(true);
			disableAll();
		} else {
			isRunningCheck.setChecked(false);
			enableAll();
		}

		if (!runRootCommand("ls")) {
			isRoot = false;
		} else {
			isRoot = true;
		}

		if (!isRoot) {

			isAutoSetProxyCheck.setChecked(false);
			isAutoSetProxyCheck.setEnabled(false);
		}

		if (!isWorked(SERVICE_NAME)) {
			CopyAssets();
			runCommand("chmod 777 /data/data/org.sshtunnel/iptables_g1");
			runCommand("chmod 777 /data/data/org.sshtunnel/iptables_n1");
			runCommand("chmod 777 /data/data/org.sshtunnel/redsocks");
			runCommand("chmod 777 /data/data/org.sshtunnel/proxy_http.sh");
			runCommand("chmod 777 /data/data/org.sshtunnel/proxy_socks.sh");
		}

	}

	/** Called when the activity is closed. */
	@Override
	public void onDestroy() {

		super.onDestroy();
	}

	/** Called when connect button is clicked. */
	public boolean serviceStart() {

		if (isWorked(SERVICE_NAME)) {

			try {
				stopService(new Intent(SSHTunnel.this, SSHTunnelService.class));
			} catch (Exception e) {
				// Nothing
			}

			return false;
		}

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);

		isAutoConnect = settings.getBoolean("isAutoConnect", false);
		isAutoSetProxy = settings.getBoolean("isAutoSetProxy", false);
		isAutoReconnect = settings.getBoolean("isAutoReconnect", false);
		isSocks = settings.getBoolean("isSocks", false);

		host = settings.getString("host", "");
		if (isTextEmpty(host, getString(R.string.host_empty)))
			return false;

		user = settings.getString("user", "");
		if (isTextEmpty(user, getString(R.string.user_empty)))
			return false;

		password = settings.getString("password", "");

		try {
			String portText = settings.getString("port", "");
			if (isTextEmpty(portText, getString(R.string.port_empty)))
				return false;
			port = Integer.valueOf(portText);

			String localPortText = settings.getString("localPort", "");
			if (isTextEmpty(localPortText, getString(R.string.local_port_empty)))
				return false;
			localPort = Integer.valueOf(localPortText);
			if (localPort <= 1024)
				this.showAToast(getString(R.string.port_alert));

			if (!isSocks) {
				String remotePortText = settings.getString("remotePort", "");
				if (isTextEmpty(remotePortText,
						getString(R.string.remote_port_empty)))
					return false;
				remotePort = Integer.valueOf(remotePortText);
			} else {
				remotePort = 0;
			}
		} catch (NumberFormatException e) {
			showAToast(getString(R.string.number_alert));
			Log.e(TAG, "wrong number", e);
			return false;
		}

		try {

			Intent it = new Intent(SSHTunnel.this, SSHTunnelService.class);
			Bundle bundle = new Bundle();
			bundle.putString("host", host);
			bundle.putString("user", user);
			bundle.putString("password", password);
			bundle.putInt("port", port);
			bundle.putInt("localPort", localPort);
			bundle.putInt("remotePort", remotePort);
			bundle.putBoolean("isAutoReconnect", isAutoReconnect);
			bundle.putBoolean("isAutoSetProxy", isAutoSetProxy);
			bundle.putBoolean("isSocks", isSocks);

			it.putExtras(bundle);
			startService(it);

		} catch (Exception ignore) {
			// Nothing
			return false;
		}

		return true;
	}

	private void showAToast(String msg) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(msg)
				.setCancelable(false)
				.setNegativeButton(getString(R.string.ok_iknow),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private void disableAll() {
		hostText.setEnabled(false);
		portText.setEnabled(false);
		userText.setEnabled(false);
		passwordText.setEnabled(false);
		localPortText.setEnabled(false);
		remotePortText.setEnabled(false);
		proxyedApps.setEnabled(false);

		isSocksCheck.setEnabled(false);
		isAutoSetProxyCheck.setEnabled(false);
		isAutoConnectCheck.setEnabled(false);
		isAutoReconnectCheck.setEnabled(false);
	}

	private void enableAll() {
		hostText.setEnabled(true);
		portText.setEnabled(true);
		userText.setEnabled(true);
		passwordText.setEnabled(true);
		localPortText.setEnabled(true);
		if (!isSocksCheck.isChecked())
			remotePortText.setEnabled(true);
		if (!isAutoSetProxyCheck.isChecked())
			proxyedApps.setEnabled(true);

		isAutoSetProxyCheck.setEnabled(true);
		isSocksCheck.setEnabled(true);
		isAutoConnectCheck.setEnabled(true);
		isAutoReconnectCheck.setEnabled(true);
	}

	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
			Preference preference) {

		if (preference.getKey() != null
				&& preference.getKey().equals("proxyedApps")) {
			Intent intent = new Intent(this, AppManager.class);
			startActivity(intent);
		} else if (preference.getKey() != null
				&& preference.getKey().equals("isRunning")) {

			if (!serviceStart()) {

				SharedPreferences settings = PreferenceManager
						.getDefaultSharedPreferences(SSHTunnel.this);

				Editor edit = settings.edit();

				edit.putBoolean("isRunning", false);

				edit.commit();

				enableAll();
			}

		}
		return super.onPreferenceTreeClick(preferenceScreen, preference);
	}

	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);

		if (settings.getBoolean("isAutoSetProxy", false))
			proxyedApps.setEnabled(false);
		else
			proxyedApps.setEnabled(true);

		if (settings.getBoolean("isSocks", false))
			remotePortText.setEnabled(false);
		else
			remotePortText.setEnabled(true);

		Editor edit = settings.edit();

		if (this.isWorked(SERVICE_NAME)) {
			edit.putBoolean("isRunning", true);
		} else {
			if (settings.getBoolean("isRunning", false)) {
				showAToast(getString(R.string.crash_alert));
				recovery();
			}
			edit.putBoolean("isRunning", false);
		}

		edit.commit();

		if (settings.getBoolean("isRunning", false)) {
			isRunningCheck.setChecked(true);
			disableAll();
		} else {
			isRunningCheck.setChecked(false);
			enableAll();
		}

		// Setup the initial values
		if (!settings.getString("user", "").equals(""))
			userText.setSummary(settings.getString("user",
					getString(R.string.user_summary)));
		if (!settings.getString("port", "").equals(""))
			portText.setSummary(settings.getString("port",
					getString(R.string.port_summary)));
		if (!settings.getString("host", "").equals(""))
			hostText.setSummary(settings.getString("host",
					getString(R.string.host_summary)));
		if (!settings.getString("password", "").equals(""))
			passwordText.setSummary("*********");
		if (!settings.getString("localPort", "").equals(""))
			localPortText.setSummary(settings.getString("localPort",
					getString(R.string.local_port_summary)));
		if (!settings.getString("remotePort", "").equals(""))
			remotePortText.setSummary(settings.getString("remotePort",
					getString(R.string.remote_port_summary)));

		// Set up a listener whenever a key changes
		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();

		// Unregister the listener whenever a key changes
		getPreferenceScreen().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	public void onSharedPreferenceChanged(SharedPreferences settings, String key) {
		// Let's do something a preference value changes

		if (key.equals("isConnecting")) {
			if (settings.getBoolean("isConnecting", false)) {
				Log.d(TAG, "Connecting start");
				pd = ProgressDialog.show(this, "",
						getString(R.string.connecting), true, false);
			} else {
				Log.d(TAG, "Connecting finish");
				if (pd != null) {
					pd.dismiss();
					pd = null;
				}
			}
		}

		if (key.equals("isSocks")) {
			if (settings.getBoolean("isSocks", false))
				remotePortText.setEnabled(false);
			else
				remotePortText.setEnabled(true);
		}

		if (key.equals("isAutoSetProxy")) {
			if (settings.getBoolean("isAutoSetProxy", false))
				proxyedApps.setEnabled(false);
			else
				proxyedApps.setEnabled(true);
		}

		if (key.equals("isRunning")) {
			if (settings.getBoolean("isRunning", false)) {
				disableAll();
				isRunningCheck.setChecked(true);
			} else {
				enableAll();
				isRunningCheck.setChecked(false);
			}
		}

		if (key.equals("user"))
			if (settings.getString("user", "").equals(""))
				userText.setSummary(getString(R.string.user_summary));
			else
				userText.setSummary(settings.getString("user", ""));
		else if (key.equals("port"))
			if (settings.getString("port", "").equals(""))
				portText.setSummary(getString(R.string.port_summary));
			else
				portText.setSummary(settings.getString("port", ""));
		else if (key.equals("host"))
			if (settings.getString("host", "").equals(""))
				hostText.setSummary(getString(R.string.host_summary));
			else
				hostText.setSummary(settings.getString("host", ""));
		else if (key.equals("localPort"))
			if (settings.getString("localPort", "").equals(""))
				localPortText
						.setSummary(getString(R.string.local_port_summary));
			else
				localPortText.setSummary(settings.getString("localPort", ""));
		else if (key.equals("remotePort"))
			if (settings.getString("remotePort", "").equals(""))
				remotePortText
						.setSummary(getString(R.string.remote_port_summary));
			else
				remotePortText.setSummary(settings.getString("remotePort", ""));
		else if (key.equals("password"))
			if (!settings.getString("password", "").equals(""))
				passwordText.setSummary("*********");
			else
				passwordText.setSummary(getString(R.string.password_summary));
	}

	// 点击Menu时，系统调用当前Activity的onCreateOptionsMenu方法，并传一个实现了一个Menu接口的menu对象供你使用
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		/*
		 * add()方法的四个参数，依次是： 1、组别，如果不分组的话就写Menu.NONE,
		 * 2、Id，这个很重要，Android根据这个Id来确定不同的菜单 3、顺序，那个菜单现在在前面由这个参数的大小决定
		 * 4、文本，菜单的显示文本
		 */
		menu.add(Menu.NONE, Menu.FIRST + 1, 1, getString(R.string.recovery))
				.setIcon(android.R.drawable.ic_menu_delete);
		menu.add(Menu.NONE, Menu.FIRST + 2, 2, getString(R.string.about))
				.setIcon(android.R.drawable.ic_menu_info_details);
		// return true才会起作用
		return true;

	}

	// 菜单项被选择事件
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case Menu.FIRST + 1:
			recovery();
			break;
		case Menu.FIRST + 2:
			showAToast(getString(R.string.copy_rights));
			break;
		}

		return true;
	}

	private void recovery() {
		try {
			stopService(new Intent(this, SSHTunnelService.class));
		} catch (Exception e) {
			// Nothing
		}

		try {
			File cache = new File(SSHTunnelService.BASE + "cache/dnscache");
			if (cache.exists())
				cache.delete();
		} catch (Exception ignore) {
			// Nothing
		}

		if (SSHTunnelService.isARMv6()) {
			runRootCommand(SSHTunnelService.BASE
					+ "iptables_g1 -t nat -F OUTPUT");
		} else {
			runRootCommand(SSHTunnelService.BASE
					+ "iptables_n1 -t nat -F OUTPUT");
		}

		runRootCommand(SSHTunnelService.BASE + "proxy_http.sh stop");
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) { // 按下的如果是BACK，同时没有重复
			try {
				finish();
			} catch (Exception ignore) {
				// Nothing
			}
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

}