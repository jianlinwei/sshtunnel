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

	private Process process;

	public void addProcess(Process p) {
		this.process = p;
	}

	public void addMonitor(ConnectionMonitor cm) {
		this.cm = cm;
	}

	@Override
	public void run() {
		try {
			while (!closed) {

				if (reconnect > 0) {
					cm.notifySuccess();
					reconnect = 0;
				}

				process.waitFor();

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
		}
	}

	public void close() {
		closed = true;
	}

}
