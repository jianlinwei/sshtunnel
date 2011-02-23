package org.sshtunnel;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import android.util.Log;

public class SSHMonitor implements Runnable {

	private static final String TAG = "SSHMonitor";
	private static final int RECONNECT_TRIES = 3;
	
	private ConnectionMonitor cm = null;
	private boolean closed = false;
	private int reconnect = 0;

	public void addMonitor(ConnectionMonitor cm) {
		this.cm = cm;
	}

	@Override
	public void run() {
		Process process = null;
		DataOutputStream os = null;
		DataInputStream in = null;
		try {
			while (!closed) {
				process = Runtime.getRuntime().exec("/system/bin/sh");
				os = new DataOutputStream(process.getOutputStream());
				os.writeBytes("ps\n");
				os.writeBytes("exit\n");
				os.flush();
				process.waitFor();
				in = new DataInputStream(process.getInputStream());
				boolean isRunning = false;
				while (true) {
					String line = in.readLine();
					if (line == null || line.equals(""))
						break;
					
					// Debug
					// Log.d(TAG, line);
					
					if (line.contains("/data/data/org.sshtunnel/ssh")) {
						isRunning = true;
						break;
					}
				}
				
				if (!isRunning) {
					// service not running

					if (reconnect > RECONNECT_TRIES) {
						
						// cannot reconnect anymore
						cm.connectionLost(false);
						closed = true;
						break;
					}
					
					// reconnect now
					reconnect++;

					cm.connectionLost(true);
					
				} else {
					if (reconnect > 0) {
						cm.notifySuccess();
                        reconnect = 0;
					}
				}
				
				try {
					Thread.sleep(5000);
				} catch (Exception ignore) {
					// Nothing
				}
				
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
			return;
		} finally {
			try {
				if (os != null)
					os.close();
				if (process != null)
					process.destroy();
			} catch (Exception e) {
				// nothing
			}
		}
	}
	
	
	public void close() {
		closed = true;
	}

}
