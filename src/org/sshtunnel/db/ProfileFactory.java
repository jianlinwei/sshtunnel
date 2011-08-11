package org.sshtunnel.db;

import java.sql.SQLException;
import java.util.List;

import org.sshtunnel.utils.Constraints;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.util.Log;

public class ProfileFactory {
	private static Profile profile;
	private static final String TAG = "SSHTunnelDB";
	
	public static Profile getProfile(Context ctx) {
		if (profile == null) {
			initProfile(ctx);
		}
		loadFromPreference(ctx);
		return profile;
	}

	public static void newProfile(Context ctx) {
		profile = new Profile();
		saveToDao(ctx);
	}

	public static void initProfile(Context ctx) {

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(ctx);

		int id = settings.getInt(Constraints.ID, -1);

		try {
			Dao<Profile, Integer> profileDao = ((DatabaseHelper) OpenHelperManager
					.getHelper(ctx)).getProfileDao();
			profile = profileDao.queryForId(id);
		} catch (SQLException e) {
			Log.e(TAG, "Cannot open DAO");
		}

		if (profile == null) {
			profile = new Profile();
		}
	}

	public static void loadFromPreference(Context ctx) {

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(ctx);

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

		profile.isAutoConnect = settings.getBoolean(
				Constraints.IS_AUTO_CONNECT, false);
		profile.isAutoReconnect = settings.getBoolean(
				Constraints.IS_AUTO_RECONNECT, false);
		profile.isAutoSetProxy = settings.getBoolean(
				Constraints.IS_AUTO_SETPROXY, false);
		profile.isSocks = settings.getBoolean(Constraints.IS_SOCKS, false);
		profile.isGFWList = settings.getBoolean(Constraints.IS_GFW_LIST, false);

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

		saveToDao(ctx);
	}

	public static void saveToPreference(Context ctx) {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(ctx);

		Editor ed = settings.edit();
		ed = settings.edit();

		ed.putInt(Constraints.ID, profile.id);
		ed.putString(Constraints.NAME, profile.name);
		ed.putString(Constraints.HOST, profile.host);
		ed.putString(Constraints.USER, profile.user);
		ed.putString(Constraints.PASSWORD, profile.password);
		ed.putString(Constraints.REMOTE_ADDRESS, profile.remoteAddress);
		ed.putString(Constraints.SSID, profile.ssid);
		ed.putString(Constraints.KEY_PATH, profile.proxyedApps);
		ed.putString(Constraints.KEY_PATH, profile.keyPath);

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

		ed.commit();
	}

	public static void saveToDao(Context ctx) {
		try {
			Dao<Profile, Integer> profileDao = ((DatabaseHelper) OpenHelperManager
					.getHelper(ctx)).getProfileDao();
			profileDao.createOrUpdate(profile);
		} catch (SQLException e) {
			Log.e(TAG, "Cannot open DAO");
		}
	}

	public static List<Profile> loadFromDao(Context ctx) {
		try {
			Dao<Profile, Integer> profileDao = ((DatabaseHelper) OpenHelperManager
					.getHelper(ctx)).getProfileDao();
			List<Profile> list = profileDao.queryForAll();
			return list;
		} catch (SQLException e) {
			Log.e(TAG, "Cannot open DAO");
		}
		return null;
	}
	
	public static Profile loadProfileFromDao(Context ctx, int profileId) {
		try {
			Dao<Profile, Integer> profileDao = ((DatabaseHelper) OpenHelperManager
					.getHelper(ctx)).getProfileDao();
			return profileDao.queryForId(profileId);
		} catch (SQLException e) {
			Log.e(TAG, "Cannot open DAO");
		}
		return null;
	}

	public static boolean delFromDao(Context ctx) {
		try {
			Dao<Profile, Integer> profileDao = ((DatabaseHelper) OpenHelperManager
					.getHelper(ctx)).getProfileDao();
			if (profileDao.delete(profile) != 1)
				return false;
			return true;
		} catch (SQLException e) {
			Log.e(TAG, "Cannot open DAO");
			return false;
		}
	}

	public static void loadFromDaoToPreference(Context ctx, int profileId) {
		try {
			Dao<Profile, Integer> profileDao = ((DatabaseHelper) OpenHelperManager
					.getHelper(ctx)).getProfileDao();
			profile = profileDao.queryForId(profileId);
		} catch (SQLException e) {
			Log.e(TAG, "Cannot open DAO");
			return;
		}
		saveToPreference(ctx);
	}
}
