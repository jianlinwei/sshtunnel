package org.sshtunnel;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

public class SSHTunnel extends Activity {

	private static final String TAG = "SSHTunnel";
	public static final String PREFS_NAME = "SSHTunnel";

	private String host;
	private int port;
	private int localPort;
	private int remotePort;
	private String user;
	private String passwd;
	private boolean isSaved = false;
	public static boolean isConnected = false;
	public static boolean isAutoStart = false;


	/** Called when the activity is closed. */
	@Override
	public void onDestroy() {
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);

		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean("IsConnected", isConnected);

		editor.commit();
		super.onDestroy();
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);

		isConnected = settings.getBoolean("IsConnected", false);
		isSaved = settings.getBoolean("IsSaved", false);

		if (isSaved) {
			host = settings.getString("Host", "");
			user = settings.getString("User", "");
			passwd = settings.getString("Password", "");
			port = settings.getInt("Port", 0);
			localPort = settings.getInt("LocalPort", 0);
			remotePort = settings.getInt("RemotePort", 0);
			isAutoStart = settings.getBoolean("IsAutoStart", false);

			final EditText hostText = (EditText) findViewById(R.id.host);
			final EditText portText = (EditText) findViewById(R.id.port);
			final EditText userText = (EditText) findViewById(R.id.user);
			final EditText passwdText = (EditText) findViewById(R.id.passwd);
			final EditText localPortText = (EditText) findViewById(R.id.localPort);
			final EditText remotePortText = (EditText) findViewById(R.id.remotePort);
			final CheckBox isAutoStartText = (CheckBox) findViewById(R.id.isAutoStart);

			hostText.setText(host);
			portText.setText(Integer.toString(port));
			userText.setText(user);
			passwdText.setText(passwd);
			localPortText.setText(Integer.toString(localPort));
			remotePortText.setText(Integer.toString(remotePort));
			isAutoStartText.setChecked(isAutoStart);
		}
	}

	/** Called when disconnect button is clicked. */
	public void serviceStop(View view) {

		try {
			stopService(new Intent(this, SSHTunnelService.class));
		} catch (Exception e) {
			// Nothing
		}

	}

	private void showAToast (String msg) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(msg)
		       .setCancelable(false)
		       .setNegativeButton("Ok, I know", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                dialog.cancel();
		           }
		       });
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	private boolean isTextEmpty(String s, String msg) {
		if (s == null || s.length() <= 0) {
			showAToast(msg);
			return true;
		}
		return false;
	}
	
	/** Called when connect button is clicked. */
	public void serviceStart(View view) {

		final Button button = (Button) findViewById(R.id.connect);
		final EditText hostText = (EditText) findViewById(R.id.host);
		final EditText portText = (EditText) findViewById(R.id.port);
		final EditText userText = (EditText) findViewById(R.id.user);
		final EditText passwdText = (EditText) findViewById(R.id.passwd);
		final EditText localPortText = (EditText) findViewById(R.id.localPort);
		final EditText remotePortText = (EditText) findViewById(R.id.remotePort);
		final CheckBox isAutoStartText = (CheckBox) findViewById(R.id.isAutoStart);

		if (isTextEmpty(hostText.getText().toString(), "Cann't let the Host empty."))
			return;
		if (isTextEmpty(portText.getText().toString(), "Cann't let the Port empty."))
			return;
		if (isTextEmpty(userText.getText().toString(), "Cann't let the User empty."))
			return;
		if (isTextEmpty(localPortText.getText().toString(), "Cann't let the Loacal Port empty."))
			return;
		if (isTextEmpty(remotePortText.getText().toString(), "Cann't let the Remote Port empty."))
			return;
		
		host = hostText.getText().toString();
		user = userText.getText().toString();
		passwd = passwdText.getText().toString();
		port = Integer.parseInt(portText.getText().toString());
		localPort = Integer.parseInt(localPortText.getText().toString());
		remotePort = Integer.parseInt(remotePortText.getText().toString());
		isAutoStart = isAutoStartText.isChecked();

		button.setClickable(false);

		try {

			Intent it = new Intent(this, SSHTunnelService.class);
			Bundle bundle = new Bundle();
			bundle.putString("host", host);
			bundle.putString("user", user);
			bundle.putString("passwd", passwd);
			bundle.putInt("port", port);
			bundle.putInt("localPort", localPort);
			bundle.putInt("remotePort", remotePort);
			

			it.putExtras(bundle);
			startService(it);
		} catch (Exception e) {
			// Nothing
		}

		button.setClickable(true);
		isSaved = true;
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean("IsSaved", isSaved);
		editor.putBoolean("IsAutoStart", isAutoStart);
		editor.putString("Host", host);
		editor.putString("User", user);
		editor.putString("Password", passwd);
		editor.putInt("Port", port);
		editor.putInt("LocalPort", localPort);
		editor.putInt("RemotePort", remotePort);
		editor.commit();

		return;
	}

}