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
import java.util.List;

import org.sshtunnel.db.Profile;
import org.sshtunnel.db.ProfileFactory;
import org.sshtunnel.utils.Constraints;
import org.sshtunnel.utils.Utils;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.flurry.android.FlurryAgent;
import com.ksmaze.android.preference.ListPreferenceMultiSelect;

public class SSHTunnel extends PreferenceActivity implements
		OnSharedPreferenceChangeListener {

	private static final String TAG = "SSHTunnel";

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

	private BroadcastReceiver ssidReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
				Log.w(TAG, "onReceived() called uncorrectly");
				return;
			}

			loadNetworkList();
		}
	};

	private ProgressDialog pd = null;
	private static boolean isRoot = false;

	private List<Profile> profileList;

	private CheckBoxPreference isAutoConnectCheck;
	private CheckBoxPreference isAutoReconnectCheck;
	private CheckBoxPreference isAutoSetProxyCheck;
	private CheckBoxPreference isSocksCheck;
	private CheckBoxPreference isGFWListCheck;
	private CheckBoxPreference isDNSProxyCheck;
	private CheckBoxPreference isUpstreamProxyCheck;

	private ListPreference profileListPreference;

	private EditTextPreference hostText;
	private EditTextPreference portText;
	private EditTextPreference userText;
	private EditTextPreference passwordText;
	private EditTextPreference localPortText;
	private EditTextPreference remotePortText;
	private EditTextPreference remoteAddressText;
	private EditTextPreference upstreamProxyText;

	private CheckBoxPreference isRunningCheck;

	private Preference proxyedApps;
	private ListPreferenceMultiSelect ssidListPreference;

	private static final int MSG_UPDATE_FINISHED = 0;

	final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_UPDATE_FINISHED:
				Toast.makeText(SSHTunnel.this,
						getString(R.string.update_finished), Toast.LENGTH_LONG)
						.show();
				break;
			}
			super.handleMessage(msg);
		}
	};

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

		if (profileList.size() > 1) {
			// del current profile
			boolean result = ProfileFactory.delFromDao();
			if (result == false)
				Log.e(TAG, "del profile error");

			// refresh profile list
			loadProfileList();

			// save the next profile to preference
			ProfileFactory.saveToPreference();

			// switch to the last profile
			Profile profile = ProfileFactory.getProfile();
			int id = profile.getId();
			SharedPreferences settings = PreferenceManager
					.getDefaultSharedPreferences(this);
			Editor ed = settings.edit();
			ed.putString(Constraints.ID, Integer.toString(id));
			ed.commit();

			// change the profile list value
			profileListPreference.setValue(Integer.toString(id));
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
		profileListPreference.setEnabled(false);
		ssidListPreference.setEnabled(false);
		upstreamProxyText.setEnabled(false);

		isSocksCheck.setEnabled(false);
		isAutoSetProxyCheck.setEnabled(false);
		isAutoConnectCheck.setEnabled(false);
		isAutoReconnectCheck.setEnabled(false);
		isGFWListCheck.setEnabled(false);
		isDNSProxyCheck.setEnabled(false);
		isUpstreamProxyCheck.setEnabled(false);
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
		if (!isGFWListCheck.isChecked()) {
			isAutoSetProxyCheck.setEnabled(true);
			if (!isAutoSetProxyCheck.isChecked())
				proxyedApps.setEnabled(true);
		}
		if (isAutoConnectCheck.isChecked()) {
			ssidListPreference.setEnabled(true);
		}
		if (isUpstreamProxyCheck.isChecked()) {
			upstreamProxyText.setEnabled(true);
		}

		profileListPreference.setEnabled(true);
		isGFWListCheck.setEnabled(true);
		isSocksCheck.setEnabled(true);
		isAutoConnectCheck.setEnabled(true);
		isAutoReconnectCheck.setEnabled(true);
		isDNSProxyCheck.setEnabled(true);
		isUpstreamProxyCheck.setEnabled(true);
	}

	private String getVersionName() {
		try {
			return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
			return "NONE";
		}
	}

	private void initProfileList() {
		profileList = ProfileFactory.loadAllProfilesFromDao();

		if (profileList == null || profileList.size() == 0) {
			ProfileFactory.newProfile();
			Profile profile = ProfileFactory.getProfile();
			profile.setName(getString(R.string.profile_default));
			ProfileFactory.saveToDao();
			ProfileFactory.saveToPreference();
		}

		loadProfileList();
	}

	private boolean isTextEmpty(String s, String msg) {
		if (s == null || s.length() <= 0) {
			showAToast(msg);
			return true;
		}
		return false;
	}

	private void loadNetworkList() {
		WifiManager wm = (WifiManager) this
				.getSystemService(Context.WIFI_SERVICE);
		List<WifiConfiguration> wcs = wm.getConfiguredNetworks();
		String[] ssidEntries = new String[wcs.size() + 3];
		ssidEntries[0] = Constraints.WIFI_AND_3G;
		ssidEntries[1] = Constraints.ONLY_WIFI;
		ssidEntries[2] = Constraints.ONLY_3G;
		int n = 3;
		for (WifiConfiguration wc : wcs) {
			if (wc != null && wc.SSID != null)
				ssidEntries[n++] = wc.SSID.replace("\"", "");
			else
				ssidEntries[n++] = "unknown";
		}
		ssidListPreference.setEntries(ssidEntries);
		ssidListPreference.setEntryValues(ssidEntries);
	}

	private void loadProfileList() {

		profileList = ProfileFactory.loadAllProfilesFromDao();

		String[] profileEntries = new String[profileList.size() + 1];
		String[] profileValues = new String[profileList.size() + 1];
		int index = 0;
		for (Profile profile : profileList) {
			profileEntries[index] = Utils.getProfileName(profile);
			profileValues[index] = Integer.toString(profile.getId());
			index++;
		}

		profileEntries[index] = getString(R.string.profile_new);
		profileValues[index] = Integer.toString(-1);

		profileListPreference.setEntries(profileEntries);
		profileListPreference.setEntryValues(profileValues);
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
		upstreamProxyText = (EditTextPreference) findPreference("upstreamProxy");

		proxyedApps = findPreference("proxyedApps");
		profileListPreference = (ListPreference) findPreference("profile_id");
		ssidListPreference = (ListPreferenceMultiSelect) findPreference("ssid");

		isRunningCheck = (CheckBoxPreference) findPreference("isRunning");
		isAutoSetProxyCheck = (CheckBoxPreference) findPreference("isAutoSetProxy");
		isSocksCheck = (CheckBoxPreference) findPreference("isSocks");
		isAutoConnectCheck = (CheckBoxPreference) findPreference("isAutoConnect");
		isAutoReconnectCheck = (CheckBoxPreference) findPreference("isAutoReconnect");
		isGFWListCheck = (CheckBoxPreference) findPreference("isGFWList");
		isDNSProxyCheck = (CheckBoxPreference) findPreference("isDNSProxy");
		isUpstreamProxyCheck = (CheckBoxPreference) findPreference("isUpstreamProxy");

		registerReceiver(ssidReceiver, new IntentFilter(
				android.net.ConnectivityManager.CONNECTIVITY_ACTION));
		loadNetworkList();

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);

		Editor edit = settings.edit();

		if (Utils.isWorked()) {
			edit.putBoolean("isRunning", true);
		} else {
			if (settings.getBoolean("isRunning", false)) {
				// showAToast(getString(R.string.crash_alert));
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

		initProfileList();

		if (!settings.getBoolean(getVersionName(), false)) {
			new Thread() {
				@Override
				public void run() {

					CopyAssets();
					runCommand("chmod 777 /data/data/org.sshtunnel/iptables");
					runCommand("chmod 777 /data/data/org.sshtunnel/redsocks");
					runCommand("chmod 777 /data/data/org.sshtunnel/proxy_http.sh");
					runCommand("chmod 777 /data/data/org.sshtunnel/proxy_socks.sh");

					SharedPreferences settings = PreferenceManager
							.getDefaultSharedPreferences(SSHTunnel.this);

					String versionName = getVersionName();

					Editor edit = settings.edit();
					edit = settings.edit();
					edit.putBoolean(versionName, true);
					edit.commit();

					handler.sendEmptyMessage(MSG_UPDATE_FINISHED);
				}
			}.start();
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
		menu.add(Menu.NONE, Menu.FIRST + 3, 1, getString(R.string.about))
				.setIcon(android.R.drawable.ic_menu_info_details);
		menu.add(Menu.NONE, Menu.FIRST + 4, 2, getString(R.string.key_manager))
				.setIcon(android.R.drawable.ic_menu_manage);
		menu.add(Menu.NONE, Menu.FIRST + 1, 3, getString(R.string.recovery))
				.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		menu.add(Menu.NONE, Menu.FIRST + 5, 4, getString(R.string.change_name))
				.setIcon(android.R.drawable.ic_menu_edit);
		menu.add(Menu.NONE, Menu.FIRST + 2, 5, getString(R.string.profile_del))
				.setIcon(android.R.drawable.ic_menu_delete);

		// return true才会起作用
		return true;

	}

	/** Called when the activity is closed. */
	@Override
	public void onDestroy() {

		unregisterReceiver(ssidReceiver);
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
			CopyAssets();
			runCommand("chmod 777 /data/data/org.sshtunnel/iptables");
			runCommand("chmod 777 /data/data/org.sshtunnel/redsocks");
			runCommand("chmod 777 /data/data/org.sshtunnel/proxy_http.sh");
			runCommand("chmod 777 /data/data/org.sshtunnel/proxy_socks.sh");
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
		case Menu.FIRST + 5:
			rename();
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

	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);

		if (settings.getBoolean("isGFWList", false)) {
			isAutoSetProxyCheck.setEnabled(false);
			proxyedApps.setEnabled(false);
		} else {
			isAutoSetProxyCheck.setEnabled(true);
			if (settings.getBoolean("isAutoSetProxy", false))
				proxyedApps.setEnabled(false);
			else
				proxyedApps.setEnabled(true);
		}

		if (settings.getBoolean("isSocks", false)) {
			remotePortText.setEnabled(false);
			remoteAddressText.setEnabled(false);
		} else {
			remotePortText.setEnabled(true);
			remoteAddressText.setEnabled(true);
		}

		Editor edit = settings.edit();

		if (Utils.isWorked()) {
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

		if (settings.getBoolean("isAutoConnecting", false)) {
			ssidListPreference.setEnabled(true);
		} else {
			ssidListPreference.setEnabled(false);
		}

		if (settings.getBoolean("isUpstreamProxy", false)) {
			upstreamProxyText.setEnabled(true);
		} else {
			upstreamProxyText.setEnabled(false);
		}

		if (settings.getBoolean("isRunning", false)) {
			isRunningCheck.setChecked(true);
			disableAll();
		} else {
			isRunningCheck.setChecked(false);
			enableAll();
		}

		// Setup the initial values
		Profile profile = ProfileFactory.getProfile();
		profileListPreference.setValue(Integer.toString(profile.getId()));
		profileListPreference.setSummary(Utils.getProfileName(profile));

		if (!settings.getString("ssid", "").equals(""))
			ssidListPreference.setSummary(settings.getString("ssid", ""));
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
		if (!settings.getString("upstreamProxy", "").equals(""))
			upstreamProxyText.setSummary(settings.getString("upstreamProxy",
					getString(R.string.upstream_proxy_summary)));
		

		// Set up a listener whenever a key changes
		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);
	}

	private void updateValue(Profile profile) {
		hostText.setText(profile.getHost());
		userText.setText(profile.getUser());
		passwordText.setText(profile.getPassword());
		remoteAddressText.setText(profile.getRemoteAddress());
		ssidListPreference.setValue(profile.getSsid());
		upstreamProxyText.setText(profile.getUpstreamProxy());

		portText.setText(Integer.toString(profile.getPort()));
		localPortText.setText(Integer.toString(profile.getLocalPort()));
		remotePortText.setText(Integer.toString(profile.getRemotePort()));


		isAutoReconnectCheck.setChecked(profile.isAutoReconnect());
		isDNSProxyCheck.setChecked(profile.isDNSProxy());
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences settings, String key) {
		// Let's do something a preference value changes

		if (key.equals(Constraints.ID)) {

			ProfileFactory.loadFromPreference();

			String id = settings.getString(Constraints.ID, "-1");
			if (id.equals("-1")) {

				// Create a new profile
				ProfileFactory.newProfile();

				// refresh profile list
				loadProfileList();

				// save the new profile to preference
				ProfileFactory.saveToPreference();

				// switch profile again
				Profile profile = ProfileFactory.getProfile();
				String profileId = Integer.toString(profile.getId());
				Editor ed = settings.edit();
				ed.putString(Constraints.ID, profileId);
				ed.commit();

				// change profile list value
				profileListPreference.setValue(profileId);

			} else {

				int profileId;

				try {
					profileId = Integer.valueOf(id);
				} catch (NumberFormatException e) {
					profileList = ProfileFactory.loadAllProfilesFromDao();
					profileId = profileList.get(0).getId();
				}

				ProfileFactory.switchToProfile(profileId);

				Profile profile = ProfileFactory.getProfile();
				profileListPreference.setSummary(Utils.getProfileName(profile));
				updateValue(profile);

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
				isSocksCheck.setChecked(true);
				remotePortText.setEnabled(false);
				remoteAddressText.setEnabled(false);
			} else {
				isSocksCheck.setChecked(false);
				remotePortText.setEnabled(true);
				remoteAddressText.setEnabled(true);
			}
		}

		if (key.equals("isGFWList")) {
			if (settings.getBoolean("isGFWList", false)) {
				isGFWListCheck.setChecked(true);
				isAutoSetProxyCheck.setEnabled(false);
				proxyedApps.setEnabled(false);
			} else {
				isGFWListCheck.setChecked(false);
				isAutoSetProxyCheck.setEnabled(true);
				if (settings.getBoolean("isAutoSetProxy", false))
					proxyedApps.setEnabled(false);
				else
					proxyedApps.setEnabled(true);
			}
		}

		if (key.equals("isAutoConnect")) {
			if (settings.getBoolean("isAutoConnect", false)) {
				isAutoConnectCheck.setChecked(true);
				ssidListPreference.setEnabled(true);
			} else {
				isAutoConnectCheck.setChecked(false);
				ssidListPreference.setEnabled(false);
			}
		}

		if (key.equals("isAutoSetProxy")) {
			if (!settings.getBoolean("isGFWList", false)) {
				if (settings.getBoolean("isAutoSetProxy", false)) {
					isAutoSetProxyCheck.setChecked(true);
					proxyedApps.setEnabled(false);
				} else {
					isAutoSetProxyCheck.setChecked(false);
					proxyedApps.setEnabled(true);
				}
			}
		}

		if (key.equals("isUpstreamProxy")) {
			if (settings.getBoolean("isUpstreamProxy", false)) {
				isUpstreamProxyCheck.setChecked(true);
				upstreamProxyText.setEnabled(true);
			} else {
				isUpstreamProxyCheck.setChecked(false);
				upstreamProxyText.setEnabled(false);
			}
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

		if (key.equals("ssid"))
			if (settings.getString("ssid", "").equals(""))
				ssidListPreference.setSummary(getString(R.string.ssid_summary));
			else
				ssidListPreference.setSummary(settings.getString("ssid", ""));
		else if (key.equals("user"))
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
		else if (key.equals("upstreamProxy"))
			if (settings.getString("upstreamProxy", "").equals(""))
				upstreamProxyText
						.setSummary(getString(R.string.upstream_proxy_summary));
			else
				upstreamProxyText.setSummary(settings.getString(
						"upstreamProxy", ""));
	}

	@Override
	public void onStart() {
		super.onStart();
		FlurryAgent.onStartSession(this, "MBY4JL18FQK1DPEJ5Y39");
	}

	@Override
	public void onStop() {
		super.onStop();
		FlurryAgent.onEndSession(this);
	}

	private void recovery() {

		new Thread() {
			@Override
			public void run() {

				try {
					stopService(new Intent(SSHTunnel.this,
							SSHTunnelService.class));
				} catch (Exception e) {
					// Nothing
				}

				try {
					File cache = new File(SSHTunnelService.BASE
							+ "cache/dnscache");
					if (cache.exists())
						cache.delete();
				} catch (Exception ignore) {
					// Nothing
				}

				runRootCommand(SSHTunnelService.BASE
						+ "iptables -t nat -F OUTPUT");

				runRootCommand(SSHTunnelService.BASE + "proxy_http.sh stop");
			}
		}.start();
	}

	private void rename() {
		LayoutInflater factory = LayoutInflater.from(this);
		final View textEntryView = factory.inflate(
				R.layout.alert_dialog_text_entry, null);
		final EditText profileName = (EditText) textEntryView
				.findViewById(R.id.profile_name_edit);
		final Profile profile = ProfileFactory.getProfile();
		profileName.setText(Utils.getProfileName(profile));

		AlertDialog ad = new AlertDialog.Builder(this)
				.setTitle(R.string.change_name)
				.setView(textEntryView)
				.setPositiveButton(R.string.alert_dialog_ok,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int whichButton) {
								EditText profileName = (EditText) textEntryView
										.findViewById(R.id.profile_name_edit);

								String name = profileName.getText().toString();
								if (name == null)
									return;
								name = name.replace("|", "");
								if (name.length() <= 0)
									return;

								profile.setName(name);
								ProfileFactory.saveToDao();

								SharedPreferences settings = PreferenceManager
										.getDefaultSharedPreferences(SSHTunnel.this);
								Editor ed = settings.edit();
								ed.putString(Constraints.NAME,
										profile.getName());
								ed.commit();

								profileListPreference.setSummary(Utils
										.getProfileName(profile));

								loadProfileList();
							}
						})
				.setNegativeButton(R.string.alert_dialog_cancel,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int whichButton) {
								/* User clicked cancel so do some stuff */
							}
						}).create();
		ad.show();
	}

	/** Called when connect button is clicked. */
	public boolean serviceStart() {

		if (Utils.isWorked()) {

			try {
				stopService(new Intent(SSHTunnel.this, SSHTunnelService.class));
			} catch (Exception e) {
				// Nothing
			}

			return false;
		}

		Profile profile = ProfileFactory.getProfile();
		ProfileFactory.loadFromPreference();

		if (isTextEmpty(profile.getHost(), getString(R.string.host_empty)))
			return false;

		if (isTextEmpty(profile.getUser(), getString(R.string.user_empty)))
			return false;

		try {
			if (isTextEmpty(Integer.toString(profile.getPort()),
					getString(R.string.port_empty)))
				return false;

			if (isTextEmpty(Integer.toString(profile.getLocalPort()),
					getString(R.string.local_port_empty)))
				return false;
			if (profile.getLocalPort() <= 1024)
				this.showAToast(getString(R.string.port_alert));

			if (!profile.isSocks()) {
				if (isTextEmpty(Integer.toString(profile.getRemotePort()),
						getString(R.string.remote_port_empty)))
					return false;
			}
		} catch (NullPointerException e) {
			showAToast(getString(R.string.number_alert));
			Log.e(TAG, "wrong number", e);
			return false;
		}

		try {

			Intent it = new Intent(SSHTunnel.this, SSHTunnelService.class);
			Bundle bundle = new Bundle();
			bundle.putInt(Constraints.ID, profile.getId());
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
							@Override
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}

}