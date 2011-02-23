package org.sshtunnel;

public interface ConnectionMonitor {
	public void connectionLost(boolean isReconnect);
	public void notifySuccess();
}
