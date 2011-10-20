package org.sshtunnel.utils;

public class Constraints {
	public static final String ID = "profile_id";
	
	public static final String NAME = "name";
	public static final String USER = "user";
	public static final String PASSWORD = "password";
	public static final String HOST = "host";
	public static final String SSID = "ssid";
	public static final String REMOTE_ADDRESS = "remoteAddress";
	public static final String KEY_PATH = "keyPath";
	public static final String PROXYED_APPS = "proxyedApps";
	public static final String UPSTREAM_PROXY = "upstreamProxy";
	
	
	public static final String IS_AUTO_RECONNECT = "isAutoReconnect";
	public static final String IS_AUTO_CONNECT = "isAutoConnect";
	public static final String IS_AUTO_SETPROXY = "isAutoSetProxy";
	public static final String IS_GFW_LIST = "isGFWList";
	public static final String IS_SOCKS = "isSocks";
	public static final String IS_DNS_PROXY = "isDNSProxy";
	public static final String IS_ACTIVE = "isActive";
	public static final String IS_UPSTREAM_PROXY = "isUpstreamProxy";
	
	public static final String PORT = "port";
	public static final String REMOTE_PORT = "remotePort";
	public static final String LOCAL_PORT = "localPort";
	
	public static final String DEFAULT_KEY_PATH = "/sdcard/sshtunnel/key";
	public static final String DEFAULT_REMOTE_ADDRESS = "127.0.0.1";
	
	public static final String ONLY_3G = "3G";
	public static final String ONLY_WIFI = "WIFI";
	public static final String WIFI_AND_3G = "WIFI/3G";
	
	public static final String FINGER_PRINT_STATUS = "fingerPrintStatus";
	public static final String FINGER_PRINT_TYPE = "fingerPrintType";
	public static final String FINGER_PRINT = "fingerPrint";
	public static final int FINGER_PRINT_INIITIALIZE = 1;
	public static final int FINGER_PRINT_CHANGED = 2;
	public static final int FINGER_PRINT_ACCEPT = 3;
	public static final int FINGER_PRINT_DENY = 4;
	
	public static final String FINGER_PRINT_ACTION_ACCEPT = "org.sshtunnel.fingerprint.ACCEPT";
	public static final String FINGER_PRINT_ACTION_DENY = "org.sshtunnel.fingerprint.DENY";
	public static final String FINGER_PRINT_ACTION = "org.sshtunnel.fingerprint.ACTION";
}
