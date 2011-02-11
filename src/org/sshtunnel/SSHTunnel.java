package org.sshtunnel;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionInfo;
import com.trilead.ssh2.ConnectionMonitor;

import com.trilead.ssh2.LocalPortForwarder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class SSHTunnel extends Activity implements ConnectionMonitor {

	private static final String TAG = "SSHTunnel";
	public static final String PREFS_NAME = "SSHTunnel";

	private String host;
	private int port;
	private int localPort;
	private int remotePort;
	private String user;
	private String passwd;
	private boolean isConnected = false;
	private boolean isSaved = false;

	private final static int AUTH_TRIES = 1;

	private Connection connection;
	// private Session session;
	private ConnectionInfo connectionInfo;
	private boolean connected = false;
	private boolean authenticated = false;

	public void connectionLost(Throwable reason) {
		onDisconnect();
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

	private void onDisconnect() {
		connected = false;
		isConnected = false;

		if (connection != null) {
			connection.close();
			connection = null;
		}
		
		runRootCommand("/data/data/org.sshtunnel/iptables -t nat -F OUTPUT");
/*		runRootCommand("iptables -t nat -D OUTPUT -p tcp "
				+ "--dport 80 -j REDIRECT --to " + localPort);
		runRootCommand("iptables -t nat -D OUTPUT -p tcp "
				+ "--dport 443 -j REDIRECT --to " + localPort);
		runRootCommand("iptables -t nat -D OUTPUT -p tcp "
				+ "--dport 5228 -j REDIRECT --to " + localPort);*/
	}

	private void CopyAssets() {
	    AssetManager assetManager = getAssets();
	    String[] files = null;
	    try {
	        files = assetManager.list("");
	    } catch (IOException e) {
	        Log.e(TAG, e.getMessage());
	    }
	    for(int i=0; i<files.length; i++) {
	        InputStream in = null;
	        OutputStream out = null;
	        try {
	          in = assetManager.open(files[i]);
	          out = new FileOutputStream("/data/data/org.sshtunnel/" + files[i]);
	          copyFile(in, out);
	          in.close();
	          in = null;
	          out.flush();
	          out.close();
	          out = null;
	        } catch(Exception e) {
	            Log.e(TAG, e.getMessage());
	        }       
	    }
	}
	private void copyFile(InputStream in, OutputStream out) throws IOException {
	    byte[] buffer = new byte[1024];
	    int read;
	    while((read = in.read(buffer)) != -1){
	      out.write(buffer, 0, read);
	    }
	}
	
	/** Called when the activity is closed. */
	@Override
	public void onDestroy() {
		onDisconnect();
		super.onDestroy();
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		
		CopyAssets();
		
		isSaved = settings.getBoolean("IsSaved", false);

		if (isSaved) {
			host = settings.getString("Host", "");
			user = settings.getString("User", "");
			passwd = settings.getString("Password", "");
			port = settings.getInt("Port", 0);
			localPort = settings.getInt("LocalPort", 0);
			remotePort = settings.getInt("RemotePort", 0);

			final EditText hostText = (EditText) findViewById(R.id.host);
			final EditText portText = (EditText) findViewById(R.id.port);
			final EditText userText = (EditText) findViewById(R.id.user);
			final EditText passwdText = (EditText) findViewById(R.id.passwd);
			final EditText localPortText = (EditText) findViewById(R.id.localPort);
			final EditText remotePortText = (EditText) findViewById(R.id.remotePort);

			hostText.setText(host);
			portText.setText(Integer.toString(port));
			userText.setText(user);
			passwdText.setText(passwd);
			localPortText.setText(Integer.toString(localPort));
			remotePortText.setText(Integer.toString(remotePort));
		}

		Process p;
		try {
			// Preform su to get root privledges
			p = Runtime.getRuntime().exec("su");

			// Attempt to write a file to a root-only
			DataOutputStream os = new DataOutputStream(p.getOutputStream());
			os.writeBytes("echo \"Do I have root?\" >/system/sd/temporary.txt\n");

			// Close the terminal
			os.writeBytes("exit\n");
			os.flush();
			try {
				p.waitFor();
				if (p.exitValue() != 255) {
					// TODO Code to run on success
					Log.e(TAG, "root");
				} else {
					// TODO Code to run on unsuccessful
					Log.e(TAG, "not root");
				}
			} catch (InterruptedException e) {
				// TODO Code to run in interrupted exception
				Log.e(TAG, "not root");
			}
		} catch (IOException e) {
			// TODO Code to run in input/output exception
			Log.e(TAG, "not root");
		}

	}

	/** Called when login button is clicked. */
	public void login(View view) {
		if (isConnected) {

			onDisconnect();
			isConnected = false;
			connected = false;
			final Button button = (Button) findViewById(R.id.connect);
			button.setText("Connect");

		} else {

			final Button button = (Button) findViewById(R.id.connect);
			final EditText hostText = (EditText) findViewById(R.id.host);
			final EditText portText = (EditText) findViewById(R.id.port);
			final EditText userText = (EditText) findViewById(R.id.user);
			final EditText passwdText = (EditText) findViewById(R.id.passwd);
			final EditText localPortText = (EditText) findViewById(R.id.localPort);
			final EditText remotePortText = (EditText) findViewById(R.id.remotePort);

			host = hostText.getText().toString();
			port = Integer.parseInt(portText.getText().toString());
			user = userText.getText().toString();
			passwd = passwdText.getText().toString();
			localPort = Integer.parseInt(localPortText.getText().toString());
			remotePort = Integer.parseInt(remotePortText.getText().toString());

			button.setClickable(false);

			/*
			 * hostText.setEnabled(false); portText.setEnabled(false);
			 * userText.setEnabled(false); passwdText.setEnabled(false);
			 * localPortText.setEnabled(false);
			 * remotePortText.setEnabled(false);
			 */

			connect();

			button.setClickable(true);
			isSaved = true;
			SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean("IsSaved", isSaved);
			editor.putString("Host", host);
			editor.putString("User", user);
			editor.putString("Password", passwd);
			editor.putInt("Port", port);
			editor.putInt("LocalPort", localPort);
			editor.putInt("RemotePort", remotePort);
			editor.commit();
		}

		return;
	}

	public void connect() {

		connection = new Connection(host, port);
		connection.addConnectionMonitor(this);

		try {
			connection.setCompression(true);
		} catch (IOException e) {
			Log.e(TAG, "Could not enable compression!", e);
		}

		try {
			/*
			 * Uncomment when debugging SSH protocol:
			 */

			/*
			 * DebugLogger logger = new DebugLogger() {
			 * 
			 * public void log(int level, String className, String message) {
			 * Log.d("SSH", message); }
			 * 
			 * };
			 * 
			 * Logger.enabled = true; Logger.logger = logger;
			 */

			connectionInfo = connection.connect();
			connected = true;

		} catch (IOException e) {
			Log.e(TAG,
					"Problem in SSH connection thread during authentication", e);

			// Display the reason in the text.

			onDisconnect();
			return;
		}

		try {
			// enter a loop to keep trying until authentication
			int tries = 0;
			while (connected && !connection.isAuthenticationComplete()
					&& tries++ < AUTH_TRIES) {
				authenticate();

				// sleep to make sure we dont kill system
				Thread.sleep(1000);
			}
		} catch (Exception e) {
			Log.e(TAG,
					"Problem in SSH connection thread during authentication", e);
		}

	}

	private void authenticate() {
		try {
			if (connection.authenticateWithNone(user)) {
				finishConnection();
				return;
			}
		} catch (Exception e) {
			Log.d(TAG, "Host does not support 'none' authentication.");
		}

		try {

			if (connection.authenticateWithPassword(user, passwd))
				finishConnection();

		} catch (IllegalStateException e) {
			Log.e(TAG,
					"Connection went away while we were trying to authenticate",
					e);
			return;
		} catch (Exception e) {
			Log.e(TAG, "Problem during handleAuthentication()", e);
		}
	}

	/**
	 * Internal method to request actual PTY terminal once we've finished
	 * authentication. If called before authenticated, it will just fail.
	 */
	private void finishConnection() {
		authenticated = true;

		try {
			if (enablePortForward()) {
				Log.e(TAG, "Forward Successful");
				AlertDialog.Builder ad = new AlertDialog.Builder(this);
				ad.setTitle("Port Forward");
				ad.setMessage("Successful!");
				ad.show();
				final Button button = (Button) findViewById(R.id.connect);
				button.setText("Disconnect");
				isConnected = true;
				runRootCommand("/data/data/org.sshtunnel/iptables -t nat -A OUTPUT -p tcp "
						+ "--dport 80 -j REDIRECT --to " + localPort);
				runRootCommand("/data/data/org.sshtunnel/iptables -t nat -A OUTPUT -p tcp "
						+ "--dport 443 -j REDIRECT --to " + localPort);
				runRootCommand("/data/data/org.sshtunnel/iptables -t nat -A OUTPUT -p tcp "
						+ "--dport 5228 -j REDIRECT --to " + localPort);
			}

		} catch (Exception e) {
			Log.e(TAG, "Error setting up port forward during connect", e);
		}

	}

	public boolean enablePortForward() {

		if (!authenticated)
			return false;

		/*
		 * DynamicPortForwarder dpf = null;
		 * 
		 * try { dpf = connection.createDynamicPortForwarder(new
		 * InetSocketAddress( InetAddress.getLocalHost(), 1984)); } catch
		 * (Exception e) { Log.e(TAG, "Could not create dynamic port forward",
		 * e); return false; }
		 */

		LocalPortForwarder lpf1 = null;
		try {
			lpf1 = connection.createLocalPortForwarder(new InetSocketAddress(
					InetAddress.getLocalHost(), localPort), "127.0.0.1",
					remotePort);
		} catch (Exception e) {
			Log.e(TAG, "Could not create local port forward", e);
			return false;
		}

		return true;
	}

}