package org.sshtunnel.db;

import java.util.List;

import org.sshtunnel.SSHTunnelContext;
import org.sshtunnel.utils.Constraints;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.util.Log;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;

public class ProfileFactory {

	private static Profile profile;
	private static final String TAG = "SSHTunnelDB";
	private static DatabaseHelper helper;

	static {
		OpenHelperManager.setOpenHelperClass(DatabaseHelper.class);
		if (helper == null) {
			helper = ((DatabaseHelper) OpenHelperManager
					.getHelper(SSHTunnelContext.getAppContext()));
		}
	}

	public static boolean delFromDao() {
		try {
			
			// try to remove profile from dao
			Dao<Profile, Integer> profileDao = helper.getProfileDao();
			int result = profileDao.delete(profile);
			if (result != 1)
				return false;
			
			// reload the current profile
			profile = getActiveProfile();
			
			// ensure current profile is active
			profile.setActive(true);
			saveToDao();
			
			return true;
		} catch (Exception e) {
			Log.e(TAG, "Cannot open DAO");
			return false;
		}
	}

	public static Profile getProfile() {
		if (profile == null) {
			initProfile();
		}
		return profile;
	}

	private static Profile getActiveProfile() {
		try {
			Dao<Profile, Integer> profileDao = helper.getProfileDao();
			List<Profile> list = profileDao.queryForAll();
			if (list == null || list.size() == 0)
				return null;
			Profile tmp = list.get(0);
			for (Profile p : list) {
				if (p.isActive) {
					tmp = p;
					break;
				}
			}
			return tmp;
		} catch (Exception e) {
			Log.e(TAG, "Cannot open DAO");
			return null;
		}
	}

	private static void initProfile() {

		profile = getActiveProfile();

		if (profile == null) {
			profile = new Profile();
			profile.setActive(true);
			saveToPreference();
			saveToDao();
		}

	}

	public static List<Profile> loadAllProfilesFromDao() {
		try {
			Dao<Profile, Integer> profileDao = helper.getProfileDao();
			List<Profile> list = profileDao.queryForAll();
			return list;
		} catch (Exception e) {
			Log.e(TAG, "Cannot open DAO");
		}
		return null;
	}
	
	public static void switchToProfile(int profileId) {
		
		// current profile should not be null
		if (profile == null)
			return;
		
		// first save any changes to dao
		saveToDao();
		
		// deactive all profiles
		deactiveAllProfiles();
		
		// query for new profile
		Profile tmp = loadProfileFromDao(profileId);
		if (tmp != null) {
			profile = tmp;
			saveToPreference();
		}
		profile.setActive(true);
		saveToDao();
		
	}
	
	private static void deactiveAllProfiles() {
		List<Profile> list = loadAllProfilesFromDao();
		for (Profile p : list) {
			p.setActive(false);
			try {
				Dao<Profile, Integer> profileDao = helper.getProfileDao();
				profileDao.createOrUpdate(p);
			} catch (Exception e) {
				Log.e(TAG, "Cannot open DAO");
			}
		}
	}

	public static void loadFromPreference() {

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(SSHTunnelContext.getAppContext());

		profile.name = settings.getString(Constraints.NAME, "");
		profile.host = settings.getString(Constraints.HOST, "");
		profile.user = settings.getString(Constraints.USER, "");
		profile.password = settings.getString(Constraints.PASSWORD, "");
		profile.remoteAddress = settings.getString(Constraints.REMOTE_ADDRESS,
				Constraints.DEFAULT_REMOTE_ADDRESS);
		profile.ssid = settings.getString(Constraints.SSID, "");
		profile.proxyedApps = settings.getString(Constraints.PROXYED_APPS, "");
		profile.keyPath = settings.getString(Constraints.KEY_PATH,
				Constraints.DEFAULT_KEY_PATH);
		profile.upstreamProxy = settings.getString(Constraints.UPSTREAM_PROXY, "");

		profile.isAutoConnect = settings.getBoolean(
				Constraints.IS_AUTO_CONNECT, false);
		profile.isAutoReconnect = settings.getBoolean(
				Constraints.IS_AUTO_RECONNECT, false);
		profile.isAutoSetProxy = settings.getBoolean(
				Constraints.IS_AUTO_SETPROXY, false);
		profile.isSocks = settings.getBoolean(Constraints.IS_SOCKS, false);
		profile.isGFWList = settings.getBoolean(Constraints.IS_GFW_LIST, false);
		profile.isDNSProxy = settings
				.getBoolean(Constraints.IS_DNS_PROXY, true);
		profile.isUpstreamProxy = settings.getBoolean(Constraints.IS_UPSTREAM_PROXY, false);

		try {
			profile.port = Integer.valueOf(settings.getString(Constraints.PORT,
					"22"));
			profile.localPort = Integer.valueOf(settings.getString(
					Constraints.LOCAL_PORT, "1984"));
			profile.remotePort = Integer.valueOf(settings.getString(
					Constraints.REMOTE_PORT, "3128"));
		} catch (NumberFormatException e) {
			Log.e(TAG, "Exception when get preferences");
			profile = null;
			return;
		}

		saveToDao();
	}

	public static Profile loadProfileFromDao(int profileId) {
		try {
			Dao<Profile, Integer> profileDao = helper.getProfileDao();
			Profile mProfile = profileDao.queryForId(profileId);
			return mProfile;
		} catch (Exception e) {
			Log.e(TAG, "Cannot open DAO");
		}
		return null;
	}

	public static void newProfile() {
		profile = new Profile();
		saveToDao();
	}

	public static void saveToDao() {
		try {
			Dao<Profile, Integer> profileDao = helper.getProfileDao();
			profileDao.createOrUpdate(profile);
		} catch (Exception e) {
			Log.e(TAG, "Cannot open DAO");
		}
	}
	
	public static void saveToDao(Profile mProfile) {
		try {
			Dao<Profile, Integer> profileDao = helper.getProfileDao();
			profileDao.createOrUpdate(mProfile);
		} catch (Exception e) {
			Log.e(TAG, "Cannot open DAO");
		}
	}

	public static void saveToPreference() {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(SSHTunnelContext.getAppContext());

		Editor ed = settings.edit();
		ed = settings.edit();

		ed.putString(Constraints.NAME, profile.name);
		ed.putString(Constraints.HOST, profile.host);
		ed.putString(Constraints.USER, profile.user);
		ed.putString(Constraints.PASSWORD, profile.password);
		ed.putString(Constraints.REMOTE_ADDRESS, profile.remoteAddress);
		ed.putString(Constraints.SSID, profile.ssid);
		ed.putString(Constraints.KEY_PATH, profile.proxyedApps);
		ed.putString(Constraints.KEY_PATH, profile.keyPath);
		ed.putString(Constraints.UPSTREAM_PROXY, profile.upstreamProxy);

		ed.putString(Constraints.PORT, Integer.toString(profile.port));
		ed.putString(Constraints.LOCAL_PORT,
				Integer.toString(profile.localPort));
		ed.putString(Constraints.REMOTE_PORT,
				Integer.toString(profile.remotePort));

		ed.putBoolean(Constraints.IS_AUTO_CONNECT, profile.isAutoConnect);
		ed.putBoolean(Constraints.IS_AUTO_RECONNECT, profile.isAutoReconnect);
		ed.putBoolean(Constraints.IS_AUTO_SETPROXY, profile.isAutoSetProxy);
		ed.putBoolean(Constraints.IS_SOCKS, profile.isSocks);
		ed.putBoolean(Constraints.IS_GFW_LIST, profile.isGFWList);
		ed.putBoolean(Constraints.IS_DNS_PROXY, profile.isDNSProxy);
		ed.putBoolean(Constraints.IS_UPSTREAM_PROXY, profile.isUpstreamProxy);

		ed.commit();
	}
}
