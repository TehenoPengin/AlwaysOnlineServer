package com.zeitheron.alwaysonline.bukkit;

import org.bukkit.plugin.java.JavaPlugin;

import com.zeitheron.alwaysonline.host.AlwaysOnlineHost;

public class AlwaysOnlineBukkit extends JavaPlugin
{
	public static AlwaysOnlineHost host;
	
	@Override
	public void onEnable()
	{
		super.onEnable();
		
		host = new AlwaysOnlineHost();
		host.start(25593);
	}
	
	@Override
	public void onDisable()
	{
		super.onDisable();
		
		if(host != null)
		{
			host.stop();
			host = null;
		}
	}
}