package org.sshtunnel.beta;

public interface ConnectionMonitor {
	public void connectionLost(boolean isReconnect);
	public void notifySuccess();
	public void waitFor();
}
