package com.zeitheron.alwaysonline.host;

import java.net.ServerSocket;

public class AOHost
{
	public static final AlwaysOnlineHost host = new AlwaysOnlineHost();
	
	public static void main(String[] args)
	{
		host.start(25593);
	}
}