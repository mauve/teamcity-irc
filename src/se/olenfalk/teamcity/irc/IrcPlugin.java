package se.olenfalk.teamcity.irc;

import org.jdom.Element;

import jetbrains.buildServer.serverSide.MainConfigProcessor;
import jetbrains.buildServer.serverSide.SBuildServer;

public class IrcPlugin implements MainConfigProcessor {

    public static final String PLUGIN_NAME = IrcPlugin.class.getSimpleName().toLowerCase();

    private final String version = "1.0.0";
    private IrcSettings ircSettings = null;

    private SBuildServer server;
    private EventListener notifier;
    private Connection conn;

    public IrcPlugin(SBuildServer server, EventListener notifier) {
        this.server = server;
        this.notifier = notifier;
    }

    @Override
    public void readFrom(Element element) {
        ircSettings = IrcSettings.loadFrom(element);

        if(conn != null) {
            conn.quit("Reloading Teamcity config");
        }

        conn = new Connection(server, ircSettings);
        notifier.setConnection(conn);
    }

    @Override
    public void writeTo(Element element) {
        ircSettings.writeTo(element);
    }
}
