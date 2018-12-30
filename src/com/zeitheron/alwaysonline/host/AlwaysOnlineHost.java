package com.zeitheron.alwaysonline.host;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

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
	
	public void start(int port)
	{
		if(listener != null)
			throw new IllegalStateException("Host already started!");
		scheduler = Executors.newScheduledThreadPool(1);
		listener = new Thread(this::handleServerThread);
		listener.setName("AlwaysOnlineHostMain");
		listener.start();
		this.port = port;
	}
	
	protected void handleServerThread()
	{
		try
		{
			ServerSocket s = new ServerSocket(port);
			while(true)
			{
				try(Socket c = s.accept())
				{
					
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
}