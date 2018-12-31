package com.zeitheron.alwaysonline.host;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * You are free to include this class anywhere. Handles the server stuff for
 * AlwaysOnline. Feel free to suggest improvements @ <a href =
 * "https://github.com/TehenoPengin/AlwaysOnlineServer">TehenoPengin/AlwaysOnlineServer</url>
 */
public class AlwaysOnlineHost
{
	protected ScheduledExecutorService scheduler;
	protected Thread listener;
	protected int port;
	
	public final Map<String, OnlineHost> pointers = new HashMap<>();
	public final ArrayList<OnlineHost> hosts = new ArrayList<>();
	
	public OnlineHost lookup(String user)
	{
		return pointers.get(user.toLowerCase());
	}
	
	public void start(int port)
	{
		if(listener != null)
			throw new IllegalStateException("Host already started!");
		scheduler = Executors.newScheduledThreadPool(1);
		scheduler.scheduleAtFixedRate(this::handleClientChecks, 20, 30, TimeUnit.SECONDS);
		listener = new Thread(this::handleServerThread);
		listener.setName("AlwaysOnlineHostMain");
		listener.start();
		this.port = port;
	}
	
	protected void handleClientChecks()
	{
		for(int i = 0; i < hosts.size(); ++i)
		{
			OnlineHost host = hosts.get(i);
			if(!host.checkAvailable())
			{
				hosts.remove(i);
				pointers.remove(host.player.toLowerCase());
				System.out.println("Removed " + host.player + "!");
				--i;
			}
		}
		
		// Clean RAM, keep as little data as possible
		System.gc();
	}
	
	protected void handleServerThread()
	{
		try(ServerSocket s = new ServerSocket(port))
		{
			while(true)
			{
				try(Socket c = s.accept())
				{
					handleClient(c);
				} catch(Throwable err)
				{
					// I'd rather spam some console than have to restart the
					// entire host
					err.printStackTrace();
				}
			}
		} catch(IOException e)
		{
			System.err.println("Unable to start host!");
			e.printStackTrace();
			
			stop();
		}
	}
	
	protected void handleClient(Socket c) throws Throwable
	{
		DataInputStream dis = new DataInputStream(c.getInputStream());
		DataOutputStream dos = new DataOutputStream(c.getOutputStream());
		
		switch(dis.readByte())
		{
		case 0:
		{
			// Get amount of hosting players
			dos.writeInt(hosts.size());
		}
		break;
		case 1:
		{
			// Get by index
			int i = dis.readInt();
			if(i >= 0 && i < hosts.size())
			{
				OnlineHost host = hosts.get(i);
				StringBuilder reply = new StringBuilder("{");
				if(host.passwordMD5 != null)
					reply.append("\"password\":\"" + host.passwordMD5 + "\",");
				reply.append("\"owner\":\"" + host.player + "\"}");
				byte[] data = reply.toString().getBytes();
				dos.writeInt(data.length);
				dos.write(data);
			}
		}
		break;
		case 2:
		{
			// Trade index and password for IP:port
			int index = dis.readInt();
			if(index >= 0 && index < hosts.size())
			{
				OnlineHost oh = hosts.get(index);
				byte[] data = new byte[dis.readShort()];
				String md5p;
				if(data.length == 0)
					md5p = null;
				else
				{
					dis.read(data);
					md5p = MD5.encrypt(data);
				}
				
				String rem = oh.passwordMD5;
				if(rem.isEmpty())
					rem = null;
					
				// Give IP:port if password's MD5 match with
				// provided by host.
				if(Objects.equals(md5p, rem))
				{
					dos.writeBoolean(true);
					data = (oh.ip + ":" + oh.port).getBytes();
					dos.writeByte(data.length);
					dos.write(data);
				} else
					dos.writeBoolean(false);
			}
		}
		break;
		case 10:
		{
			// Add hosting player
			String ip = ((InetSocketAddress) c.getRemoteSocketAddress()).getAddress().getHostAddress();
			int port = dis.readInt();
			byte[] data = new byte[dis.readByte()];
			dis.read(data);
			String username = new String(data);
			data = new byte[dis.readByte()];
			dis.read(data);
			String passMD5 = new String(data);
			
			if(!pointers.containsKey(username.toLowerCase()))
			{
				OnlineHost oh = new OnlineHost(username, ip, passMD5, port);
				pointers.put(username.toLowerCase(), oh);
				hosts.add(oh);
				System.out.println("Hosting " + username + "!");
				dos.writeBoolean(true);
			} else
				dos.writeBoolean(false);
		}
		break;
		default:
		break;
		}
		
		dos.flush();
	}
	
	public void stop()
	{
		if(scheduler == null || listener == null)
			throw new IllegalStateException("Host not started!");
		
		scheduler.shutdownNow();
		scheduler = null;
		
		listener.interrupt();
		listener = null;
	}
	
	protected static void sleep(long ms)
	{
		try
		{
			Thread.sleep(ms);
		} catch(InterruptedException e)
		{
		}
	}
	
	protected static class MD5
	{
		public static String encrypt(byte[] data)
		{
			MessageDigest messageDigest = null;
			byte[] digest = new byte[] {};
			try
			{
				messageDigest = MessageDigest.getInstance("MD5");
				messageDigest.reset();
				messageDigest.update(data);
				digest = messageDigest.digest();
			} catch(NoSuchAlgorithmException e)
			{
				e.printStackTrace();
			}
			BigInteger bigInt = new BigInteger(1, digest);
			String md5Hex = bigInt.toString(16);
			while(md5Hex.length() < 32)
			{
				md5Hex = "0" + md5Hex;
			}
			return md5Hex;
		}
		
		public static String encrypt(String line)
		{
			return encrypt(line.getBytes());
		}
	}
	
	protected static class OnlineHost
	{
		public final String player, ip, passwordMD5;
		public final int port;
		
		public long lastStatusRefresh;
		protected ServerStatus lastStatus;
		
		public OnlineHost(String player, String ip, String passwordMD5, int port)
		{
			this.passwordMD5 = passwordMD5;
			this.player = player;
			this.ip = ip;
			this.port = port;
		}
		
		public ServerStatus getStatus()
		{
			if(lastStatus == null || System.currentTimeMillis() - lastStatusRefresh >= 10000L)
				lastStatus = ServerStatus.ping(ip, port, 2000);
			return lastStatus;
		}
		
		public boolean checkAvailable()
		{
			return getStatus().isServerUp();
		}
	}
	
	protected static class ServerStatus
	{
		public static ServerStatus emulateOffline(String ip)
		{
			return emulateOffline(ip.contains(":") ? ip.substring(0, ip.indexOf(":")) : ip, ip.contains(":") ? Integer.parseInt(ip.substring(ip.indexOf(":") + 1)) : 25565);
		}
		
		public static ServerStatus emulateOffline(String ip, int port)
		{
			ServerStatus emu = new ServerStatus();
			emu.address = ip;
			emu.port = port;
			emu.serverUp = false;
			return emu;
		}
		
		public static ServerStatus ping(String server, int timeout)
		{
			return new ServerStatus(server.contains(":") ? server.substring(0, server.indexOf(":")) : server, server.contains(":") ? Integer.parseInt(server.substring(server.indexOf(":") + 1)) : 25565, timeout);
		}
		
		public static ServerStatus ping(String server, int port, int timeout)
		{
			return new ServerStatus(server, port, timeout);
		}
		
		final byte NUM_FIELDS = 6;
		public final long pingTime = System.currentTimeMillis();
		private String address;
		private int port;
		private boolean serverUp;
		private String motd;
		private String version;
		private String currentPlayers;
		private String maximumPlayers;
		public final boolean emulated;
		
		private ServerStatus()
		{
			emulated = true;
		}
		
		public ServerStatus(String address, int port, int... timeout)
		{
			emulated = false;
			String rawServerData;
			String[] serverData;
			setAddress(address);
			setPort(port);
			
			if(timeout.length == 0 || timeout[0] < 10)
				timeout = new int[] { 2000 };
			
			try
			{
				Socket clientSocket = new Socket();
				clientSocket.connect(new InetSocketAddress(getAddress(), getPort()), timeout[0]);
				DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
				BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				byte[] payload = { (byte) 0xFE, (byte) 0x01 };
				dos.write(payload, 0, payload.length);
				rawServerData = br.readLine();
				clientSocket.close();
			} catch(Exception e)
			{
				serverUp = false;
				return;
			}
			
			if(rawServerData == null)
				serverUp = false;
			else
			{
				String n = "\u0000";
				serverData = rawServerData.split(n + n + n);
				System.out.println(serverData.length + " | " + rawServerData);
				if(serverData != null && serverData.length >= NUM_FIELDS)
				{
					serverUp = true;
					setVersion(serverData[2].replace(n, ""));
					setMotd(serverData[3].replace(n, ""));
					setCurrentPlayers(serverData[4].replace(n, ""));
					setMaximumPlayers(serverData[5].replace(n, ""));
				} else
					serverUp = false;
			}
		}
		
		public String getAddress()
		{
			return address;
		}
		
		public String getCurrentPlayers()
		{
			return currentPlayers;
		}
		
		public String getMaximumPlayers()
		{
			return maximumPlayers;
		}
		
		public String getMotd()
		{
			return motd;
		}
		
		public int getPort()
		{
			return port;
		}
		
		public String getVersion()
		{
			return version;
		}
		
		public boolean isServerUp()
		{
			return serverUp;
		}
		
		public void setAddress(String address)
		{
			this.address = address;
		}
		
		public void setCurrentPlayers(String currentPlayers)
		{
			this.currentPlayers = currentPlayers;
		}
		
		public void setMaximumPlayers(String maximumPlayers)
		{
			this.maximumPlayers = maximumPlayers;
		}
		
		public void setMotd(String motd)
		{
			this.motd = motd;
		}
		
		public void setPort(int port)
		{
			this.port = port;
		}
		
		public void setVersion(String version)
		{
			this.version = version;
		}
	}
}