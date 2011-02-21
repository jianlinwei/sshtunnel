package org.sshtunnel;

import java.net.*;
import java.util.*;

import android.util.Log;

public class DnsResponseFilter implements Runnable
{

	private DatagramSocket dsReq;

	private SocketAddress saResp;

	private static List filterIps = new ArrayList();
	private static String TAG = "DnsFilter";

	private static DatagramSocket ds;

	private DnsResponseFilter(DatagramSocket dsReq, SocketAddress saResp)
	{
		this.dsReq = dsReq;
		this.saResp = saResp;
	}

	private static int skipDnsName(byte[] buf, int offset, int len)
	{
		while(buf[offset] != 0)
		{
			if(buf[offset] < 0)
			{
				if(buf[offset] == (byte) 0xc0 && buf[offset + 1] == (byte) 0x0c)
					return offset + 2;
				throw new IllegalArgumentException();
			}
			offset += buf[offset];
			++offset;
			if(offset >= len)
				throw new ArrayIndexOutOfBoundsException(offset);
		}
		return ++offset;
	}

	private static InetAddress findRespAddr(byte[] buf, int len) throws UnknownHostException
	{
		int i = 12;
		i = skipDnsName(buf, i, len);
		i += 4;
		i = skipDnsName(buf, i, len);
		i += 10;
		if(i + 4 > len)
			throw new ArrayIndexOutOfBoundsException(i + 4);
		byte[] addr = new byte[4];
		System.arraycopy(buf, i, addr, 0, addr.length);
		return InetAddress.getByAddress(addr);
	}

	public static void startDNS()
	{
		Properties prop = new Properties();
		try
		{
			prop.load(DnsResponseFilter.class.
					getResourceAsStream("/data/data/org.sshtunnel/dnsfilter.properties"));
		}
		catch(Exception e)
		{
			e.printStackTrace();
			Log.d(TAG, "Can't find file 'dnsfilter.properties'.");
			System.exit(-1);
		}
		byte[] buf = new byte[65535];
		try
		{
			Log.d(TAG, "Starting...Please wait...");
			byte[] req = {0x00, 0x02, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x07, 0x74, 0x77, 0x69, 0x74, 0x74, 0x65, 0x72, 0x03, 0x63, 0x6f, 0x6d, 0x00, 0x00, 0x01, 0x00, 0x01};
			DatagramSocket ds = new DatagramSocket();
			ds.setSoTimeout(Integer.parseInt(prop.getProperty("TestRespTimeout")));
			InetAddress nodns = InetAddress.getByName(prop.getProperty("TestDnsServer"));
			Set filterIps = new HashSet();
			int testCouont = Integer.parseInt(prop.getProperty("TestCount"));
			for(int n = 0; n < testCouont; n++)
			{
				DatagramPacket dp = new DatagramPacket(req, req.length);
				dp.setAddress(nodns);
				dp.setPort(53);
				ds.send(dp);
				dp = new DatagramPacket(buf, buf.length);
				try
				{
					ds.receive(dp);
				}
				catch (SocketTimeoutException e)
				{
					continue;
				}
				InetAddress respAddr = null;
				try
				{
					respAddr = findRespAddr(dp.getData(), dp.getLength());
				}
				catch (ArrayIndexOutOfBoundsException e)
				{
				}
				catch (IllegalArgumentException e)
				{
				}
				if(respAddr != null)
				{
					filterIps.add(respAddr);
				}
			}
			DnsResponseFilter.filterIps.addAll(filterIps);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			Log.d(TAG, "File 'dnsfilter.properties' is incorrect.");
			System.exit(-1);
		}
		Log.d(TAG, "Start finished.  You can set the dns server to 127.0.0.1 now.");
		try
		{
			ds = new DatagramSocket(8153, InetAddress.getByName(prop.getProperty("BindToIP")));
			while(true)
			{
				DatagramPacket dp = new DatagramPacket(buf, buf.length);
				ds.receive(dp);
				SocketAddress saResp = dp.getSocketAddress();
				DatagramSocket dsReq = new DatagramSocket();
				dsReq.setSoTimeout(Integer.parseInt(prop.getProperty("ResposneTimeout")));
				dp.setAddress(InetAddress.getByName(prop.getProperty("DnsServer")));
				dp.setPort(53);
				dsReq.send(dp);
				new Thread(new DnsResponseFilter(dsReq, saResp)).start();
			}
		}
		catch (SocketException e)
		{
			e.printStackTrace();
			Log.d(TAG, "Can't bind UDP port 53 to '" + prop.getProperty("BindToIP") + "'.");
			System.exit(-1);
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
			Log.d(TAG, "Can't find host '" + prop.getProperty("BindToIP") + "'.");
			System.exit(-1);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			Log.d(TAG, "File 'dnsfilter.properties' is incorrect.");
			System.exit(-1);
		}
	}

	public void run()
	{
		byte[] buf = new byte[65535];
		while(true)
		{
			DatagramPacket dp = new DatagramPacket(buf, buf.length);
			InetAddress respAddr = null;
			try
			{
				dsReq.receive(dp);
				try
				{
					respAddr = findRespAddr(dp.getData(), dp.getLength());
				}
				catch (ArrayIndexOutOfBoundsException e)
				{
					Log.d(TAG, "WARN: DNS Response incorrect.  Ignore this.");
				}
				catch (IllegalArgumentException e)
				{
					Log.d(TAG, "WARN: DNS Response not recognize in current version.  Ignore this.");
				}
				if(respAddr != null)
				{
					boolean filtered = false;
					for(int i = 0; i < filterIps.size(); i++)
					{
						if(respAddr.equals(filterIps.get(i)))
						{
							filtered = true;
							break;
						}
					}
					if(filtered)
					{
						Log.d(TAG, "Debug: Filtered IP '" + respAddr + "'.");
						continue;
					}
				}
				dp.setSocketAddress(saResp);
				ds.send(dp);
				break;
			}
			catch (SocketTimeoutException e)
			{
				Log.d(TAG, "WARN: Receive DNS Response timeout.  Please ignore this.");
				break;
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		dsReq.close();
	}

}
