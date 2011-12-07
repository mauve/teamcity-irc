package se.olenfalk.teamcity.irc;

import org.jdom.Element;

import jetbrains.buildServer.serverSide.MainConfigProcessor;
import jetbrains.buildServer.serverSide.SBuildServer;

public class IrcPlugin implements MainConfigProcessor {

	public static final String PLUGIN_NAME = IrcPlugin.class.getSimpleName().toLowerCase();
	
	private final String version = "1.0.0";
	private IrcSettings ircSettings = null;
	
	public IrcPlugin(SBuildServer server)
	{
		
	}
	
	@Override
	public void readFrom(Element element)
	{
		ircSettings = IrcSettings.loadFrom(element);
	}

	@Override
	public void writeTo(Element element)
	{
		ircSettings.writeTo(element);
	}

}
