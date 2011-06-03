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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
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
	private ProgressDialog pd = null;
	private String host = "";
	private int port = 22;
	private int localPort = 1984;
	private int remotePort = 3128;
	private String remoteAddress = "127.0.0.1";
	private String user = "";
	private String password = "";
	private String profile;
	public static boolean isAutoConnect = false;
	public static boolean isAutoReconnect = false;
	public static boolean isAutoSetProxy = false;

	public static boolean isSocks = false;
	public static boolean isRoot = false;
	private CheckBoxPreference isAutoConnectCheck;
	private CheckBoxPreference isAutoReconnectCheck;
	private CheckBoxPreference isAutoSetProxyCheck;

	private CheckBoxPreference isSocksCheck;
	private ListPreference profileList;
	private EditTextPreference hostText;
	private EditTextPreference portText;
	private EditTextPreference userText;
	private EditTextPreference passwordText;
	private EditTextPreference localPortText;
	private EditTextPreference remotePortText;
	private EditTextPreference remoteAddressText;

	private CheckBoxPreference isRunningCheck;

	private Preference proxyedApps;

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
				Log.e(TAG, "Assets error", e);
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

	private void delProfile() {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);
		String[] profileEntries = settings.getString("profileEntries", "")
				.split("\\|");
		String[] profileValues = settings.getString("profileValues", "").split(
				"\\|");

		Log.d(TAG, "Profile :" + profile);
		if (profileEntries.length > 2) {
			StringBuffer profileEntriesBuffer = new StringBuffer();
			StringBuffer profileValuesBuffer = new StringBuffer();

			String newProfileValue = "1";

			for (int i = 0; i < profileValues.length - 1; i++) {
				if (!profile.equals(profileValues[i])) {
					profileEntriesBuffer.append(profileEntries[i] + "|");
					profileValuesBuffer.append(profileValues[i] + "|");
					newProfileValue = profileValues[i];
				}
			}
			profileEntriesBuffer.append(getString(R.string.profile_new));
			profileValuesBuffer.append("0");

			Editor ed = settings.edit();
			ed.putString("profileEntries", profileEntriesBuffer.toString());
			ed.putString("profileValues", profileValuesBuffer.toString());
			ed.putString("profile", newProfileValue);
			ed.commit();

			loadProfileList();
		}
	}

	public boolean detectRoot() {
		try {
			Process proc = Runtime.getRuntime().exec("su");
			if (proc == null)
				return false;
			proc.destroy();
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	private void disableAll() {
		hostText.setEnabled(false);
		portText.setEnabled(false);
		userText.setEnabled(false);
		passwordText.setEnabled(false);
		localPortText.setEnabled(false);
		remotePortText.setEnabled(false);
		remoteAddressText.setEnabled(false);
		proxyedApps.setEnabled(false);
		profileList.setEnabled(false);

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
		if (!isSocksCheck.isChecked()) {
			remotePortText.setEnabled(true);
			remoteAddressText.setEnabled(true);
		}
		if (!isAutoSetProxyCheck.isChecked())
			proxyedApps.setEnabled(true);

		profileList.setEnabled(true);
		isAutoSetProxyCheck.setEnabled(true);
		isSocksCheck.setEnabled(true);
		isAutoConnectCheck.setEnabled(true);
		isAutoReconnectCheck.setEnabled(true);
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

	private void loadProfileList() {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);
		String[] profileEntries = settings.getString("profileEntries", "")
				.split("\\|");
		String[] profileValues = settings.getString("profileValues", "").split(
				"\\|");

		profileList.setEntries(profileEntries);
		profileList.setEntryValues(profileValues);
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
		remoteAddressText = (EditTextPreference) findPreference("remoteAddress");
		proxyedApps = (Preference) findPreference("proxyedApps");
		profileList = (ListPreference) findPreference("profile");

		isRunningCheck = (CheckBoxPreference) findPreference("isRunning");
		isAutoSetProxyCheck = (CheckBoxPreference) findPreference("isAutoSetProxy");
		isSocksCheck = (CheckBoxPreference) findPreference("isSocks");
		isAutoConnectCheck = (CheckBoxPreference) findPreference("isAutoConnect");
		isAutoReconnectCheck = (CheckBoxPreference) findPreference("isAutoReconnect");

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);

		String profileValuesString = settings.getString("profileValues", "");

		if (profileValuesString.equals("")) {
			Editor ed = settings.edit();
			profile = "1";
			ed.putString("profileValues", "1|0");
			ed.putString("profileEntries", getString(R.string.profile_default)
					+ "|" + getString(R.string.profile_new));
			ed.putString("profile", "1");
			ed.commit();

			profileList.setDefaultValue("1");
		}

		loadProfileList();

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

		if (!detectRoot()) {
			isRoot = false;
		} else {
			isRoot = true;
		}

		if (!isRoot) {

			isAutoSetProxyCheck.setChecked(false);
			isAutoSetProxyCheck.setEnabled(false);
			proxyedApps.setEnabled(false);
			showAToast(getString(R.string.require_root_alert));
		}

		if (!isWorked(SERVICE_NAME)) {
			CopyAssets();
			runCommand("chmod 777 /data/data/org.sshtunnel/iptables");
			runCommand("chmod 777 /data/data/org.sshtunnel/redsocks");
			runCommand("chmod 777 /data/data/org.sshtunnel/proxy_http.sh");
			runCommand("chmod 777 /data/data/org.sshtunnel/proxy_socks.sh");
		}

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
				.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		menu.add(Menu.NONE, Menu.FIRST + 2, 2, getString(R.string.profile_del))
				.setIcon(android.R.drawable.ic_menu_delete);
		menu.add(Menu.NONE, Menu.FIRST + 3, 3, getString(R.string.about))
				.setIcon(android.R.drawable.ic_menu_info_details);
		menu.add(Menu.NONE, Menu.FIRST + 4, 4, getString(R.string.key_manager))
		.setIcon(android.R.drawable.ic_menu_edit);

		// return true才会起作用
		return true;

	}

	/** Called when the activity is closed. */
	@Override
	public void onDestroy() {

		super.onDestroy();
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

	// 菜单项被选择事件
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case Menu.FIRST + 1:
			recovery();
			break;
		case Menu.FIRST + 2:
			delProfile();
			break;
		case Menu.FIRST + 3:
			String versionName = "";
			try {
				versionName = getPackageManager().getPackageInfo(
						getPackageName(), 0).versionName;
			} catch (NameNotFoundException e) {
				versionName = "";
			}
			showAToast(getString(R.string.about) + " (" + versionName + ")"
					+ getString(R.string.copy_rights));
			break;
		case Menu.FIRST + 4:
			Intent intent = new Intent(this, FileChooser.class);
			startActivity(intent);
			break;
		}

		return true;
	}

	@Override
	protected void onPause() {
		super.onPause();

		// Unregister the listener whenever a key changes
		getPreferenceScreen().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
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

	private void onProfileChange(String oldProfile) {

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(SSHTunnel.this);

		isAutoConnect = settings.getBoolean("isAutoConnect", false);
		isAutoSetProxy = settings.getBoolean("isAutoSetProxy", false);
		isAutoReconnect = settings.getBoolean("isAutoReconnect", false);
		isSocks = settings.getBoolean("isSocks", false);

		host = settings.getString("host", "");

		user = settings.getString("user", "");

		password = settings.getString("password", "");

		String portString = settings.getString("port", "");
		try {
			port = Integer.valueOf(portString);
		} catch (NumberFormatException e) {
			port = 22;
		}

		String localPortString = settings.getString("localPort", "");
		try {
			localPort = Integer.valueOf(localPortString);
		} catch (NumberFormatException e) {
			localPort = 1984;
		}

		remoteAddress = settings.getString("remoteAddress", "127.0.0.1");

		String remotePortString = settings.getString("remotePort", "");
		try {
			remotePort = Integer.valueOf(remotePortString);
		} catch (NumberFormatException e) {
			remotePort = 3128;
		}

		String oldProfileSettings = host + "|" + port + "|" + user + "|"
				+ password + "|" + (isSocks ? "true" : "false") + "|"
				+ localPort + "|" + remoteAddress + "|" + remotePort;

		Editor ed = settings.edit();
		ed.putString(oldProfile, oldProfileSettings);
		ed.commit();

		String profileString = settings.getString(profile, "");

		if (profileString.equals("")) {

			host = "";
			port = 22;
			user = "";
			password = "";
			isSocks = false;
			localPort = 1984;
			remoteAddress = "127.0.0.1";
			remotePort = 3128;

		} else {

			String[] st = profileString.split("\\|");
			Log.d(TAG, "Token size: " + st.length);

			host = st[0];
			port = Integer.valueOf(st[1]);
			user = st[2];
			password = st[3];
			isSocks = st[4].equals("true") ? true : false;
			localPort = Integer.valueOf(st[5]);
			remoteAddress = st[6];
			remotePort = Integer.valueOf(st[7]);

		}

		Log.d(TAG, host + "|" + port + "|" + user + "|" + password + "|"
				+ (isSocks ? "true" : "false") + "|" + localPort + "|"
				+ remoteAddress + "|" + remotePort);

		hostText.setText(host);
		portText.setText(Integer.toString(port));
		userText.setText(user);
		passwordText.setText(password);
		isSocksCheck.setChecked(isSocks);
		localPortText.setText(Integer.toString(localPort));
		remoteAddressText.setText(remoteAddress);
		remotePortText.setText(Integer.toString(remotePort));

		ed = settings.edit();
		ed.putString("host", host.equals("null") ? "" : host);
		ed.putString("port", Integer.toString(port));
		ed.putString("user", user.equals("null") ? "" : user);
		ed.putString("password", password.equals("null") ? "" : password);
		ed.putBoolean("isSocks", isSocks);
		ed.putString("localPort", Integer.toString(localPort));
		ed.putString("remoteAddress", remoteAddress);
		ed.putString("remotePort", Integer.toString(remotePort));
		ed.commit();

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

		if (settings.getBoolean("isSocks", false)) {
			remotePortText.setEnabled(false);
			remoteAddressText.setEnabled(false);
		} else {
			remotePortText.setEnabled(true);
			remoteAddressText.setEnabled(true);
		}

		Editor edit = settings.edit();

		if (this.isWorked(SERVICE_NAME)) {
			if (settings.getBoolean("isConnecting", false))
				isRunningCheck.setEnabled(false);
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
		profile = settings.getString("profile", "1");
		profileList.setValue(profile);

		profileList.setSummary(getString(R.string.profile_base) + " "
				+ settings.getString("profile", ""));

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
		if (!settings.getString("remoteAddress", "").equals(""))
			remoteAddressText.setSummary(settings.getString("remoteAddress",
					getString(R.string.remote_port_summary)));

		// Set up a listener whenever a key changes
		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);
	}

	public void onSharedPreferenceChanged(SharedPreferences settings, String key) {
		// Let's do something a preference value changes

		if (key.equals("profile")) {
			String profileString = settings.getString("profile", "");
			if (profileString.equals("0")) {
				String[] profileEntries = settings.getString("profileEntries",
						"").split("\\|");
				String[] profileValues = settings
						.getString("profileValues", "").split("\\|");
				int newProfileValue = Integer
						.valueOf(profileValues[profileValues.length - 2]) + 1;

				StringBuffer profileEntriesBuffer = new StringBuffer();
				StringBuffer profileValuesBuffer = new StringBuffer();

				for (int i = 0; i < profileValues.length - 1; i++) {
					profileEntriesBuffer.append(profileEntries[i] + "|");
					profileValuesBuffer.append(profileValues[i] + "|");
				}
				profileEntriesBuffer.append(getString(R.string.profile_base)
						+ " " + newProfileValue + "|");
				profileValuesBuffer.append(newProfileValue + "|");
				profileEntriesBuffer.append(getString(R.string.profile_new));
				profileValuesBuffer.append("0");

				Editor ed = settings.edit();
				ed.putString("profileEntries", profileEntriesBuffer.toString());
				ed.putString("profileValues", profileValuesBuffer.toString());
				ed.putString("profile", Integer.toString(newProfileValue));
				ed.commit();

				loadProfileList();

			} else {
				String oldProfile = profile;
				profile = profileString;
				profileList.setValue(profile);
				onProfileChange(oldProfile);
				profileList.setSummary(getString(R.string.profile_base) + " "
						+ profileString);
			}
		}

		if (key.equals("isConnecting")) {
			if (settings.getBoolean("isConnecting", false)) {
				Log.d(TAG, "Connecting start");
				isRunningCheck.setEnabled(false);
				pd = ProgressDialog.show(this, "",
						getString(R.string.connecting), true, true);
			} else {
				Log.d(TAG, "Connecting finish");
				if (pd != null) {
					pd.dismiss();
					pd = null;
				}
				isRunningCheck.setEnabled(true);
			}
		}

		if (key.equals("isSocks")) {
			if (settings.getBoolean("isSocks", false)) {
				remotePortText.setEnabled(false);
				remoteAddressText.setEnabled(false);
			} else {
				remotePortText.setEnabled(true);
				remoteAddressText.setEnabled(true);
			}
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
		else if (key.equals("remoteAddress"))
			if (settings.getString("remoteAddress", "").equals(""))
				remoteAddressText
						.setSummary(getString(R.string.remote_port_summary));
			else
				remoteAddressText.setSummary(settings.getString(
						"remoteAddress", ""));
		else if (key.equals("password"))
			if (!settings.getString("password", "").equals(""))
				passwordText.setSummary("*********");
			else
				passwordText.setSummary(getString(R.string.password_summary));
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

		runRootCommand(SSHTunnelService.BASE + "iptables -t nat -F OUTPUT");

		runRootCommand(SSHTunnelService.BASE + "proxy_http.sh stop");
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
			String portString = settings.getString("port", "");
			if (isTextEmpty(portString, getString(R.string.port_empty)))
				return false;
			port = Integer.valueOf(portString);

			String localPortString = settings.getString("localPort", "");
			if (isTextEmpty(localPortString,
					getString(R.string.local_port_empty)))
				return false;
			localPort = Integer.valueOf(localPortString);
			if (localPort <= 1024)
				this.showAToast(getString(R.string.port_alert));

			if (!isSocks) {
				String remotePortString = settings.getString("remotePort", "");
				if (isTextEmpty(remotePortString,
						getString(R.string.remote_port_empty)))
					return false;
				remotePort = Integer.valueOf(remotePortString);
				remoteAddress = settings
						.getString("remoteAddress", "127.0.0.1");
			} else {
				remoteAddress = "";
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
			bundle.putString("remoteAddress", remoteAddress);
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

}