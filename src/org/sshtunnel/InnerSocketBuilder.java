package org.sshtunnel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

import android.util.Log;

public class InnerSocketBuilder {

	private String proxyHost = "10.0.0.172";
	private int proxyPort = 80;
	private String target = "";

	private Socket innerSocket = null;

	private boolean isConnected = false;

	private long starTime = System.currentTimeMillis();
	private final String TAG = "CMWRAP->InnerSocketBuilder";
	private final String UA = "biAji's wap channel";

	public InnerSocketBuilder(String target) {
		this("10.0.0.172", 80, target);
	}

	/**
	 * 建立经由代理服务器至目标服务器的连接
	 * 
	 * @param proxyHost
	 *            代理服务器地址
	 * @param proxyPort
	 *            代理服务器端口
	 * @param target
	 *            目标服务器
	 */
	public InnerSocketBuilder(String proxyHost, int proxyPort, String target) {
		this.proxyHost = proxyHost;
		this.proxyPort = proxyPort;
		this.target = target;
		connect();
	}

	private void connect() {

		// starTime = System.currentTimeMillis();
		Log.v(TAG, "建立通道");
		BufferedReader din = null;
		BufferedWriter dout = null;

		try {
			innerSocket = new Socket(proxyHost, proxyPort);
			innerSocket.setKeepAlive(true);
			innerSocket.setSoTimeout(300 * 1000);

			din = new BufferedReader(new InputStreamReader(
					innerSocket.getInputStream()));
			dout = new BufferedWriter(new OutputStreamWriter(
					innerSocket.getOutputStream()));

			String connectStr = "CONNECT " + target
					+ " HTTP/1.1\r\nUser-agent: " + this.UA + "\r\n\r\n";

			dout.write(connectStr);
			dout.flush();
			Log.v(TAG, connectStr);

			String result = din.readLine();
			String line = "";
			while ((line = din.readLine()) != null) {
				if (line.trim().equals(""))
					break;
				Log.v(TAG, line);
			}

			if (result != null && result.contains("200")) {
				Log.v(TAG, result);
				Log.v(TAG, "通道建立成功， 耗时："
						+ (System.currentTimeMillis() - starTime) / 1000);
				isConnected = true;
			} else {
				Log.d(TAG, "建立隧道失败");
			}

		} catch (IOException e) {
			Log.e(TAG, "建立隧道失败：" + e.getLocalizedMessage());
		}
	}

	public Socket getSocket() {
		return innerSocket;
	}

	public boolean isConnected() {
		return this.isConnected;
	}

}
