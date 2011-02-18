package org.sshtunnel;

import java.io.IOException;

public interface WrapServer extends Runnable {

	public abstract void close() throws IOException;

	public abstract int getServPort();

	public abstract boolean isClosed();

	public abstract void setProxyHost(String host);

	public abstract void setProxyPort(int port);

	/**
	 * 设置此服务的目的地址
	 * 
	 * @param target
	 */
	public abstract void setTarget(String target);
}
