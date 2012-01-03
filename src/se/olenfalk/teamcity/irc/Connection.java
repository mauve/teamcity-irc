package se.olenfalk.teamcity.irc;

import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildServer;

import org.schwering.irc.lib.IRCEventListener;
import org.schwering.irc.lib.IRCModeParser;
import org.schwering.irc.lib.IRCUser;
import org.schwering.irc.lib.ssl.SSLDefaultTrustManager;
import org.schwering.irc.lib.ssl.SSLIRCConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Connection implements IRCEventListener {

    private static final Logger LOG = LoggerFactory.getLogger("IRC_CONNECTION");

    private IrcSettings settings;
    private SSLIRCConnection connection;
    private boolean serverShutdown = false;
    private String currentNickname = "teamcity";
    private Set<String> channels = new HashSet<String>();
    private Timer connectTimer = new Timer();

    public Connection(SBuildServer bs, IrcSettings is) {
        settings = is;
        currentNickname = settings.nickname;

        connection = new SSLIRCConnection(settings.hostname,
                                            new int[] { settings.port },
                                            settings.password,
                                            settings.nickname,
                                            settings.username,
                                            settings.realname);
        connection.addTrustManager(new SSLDefaultTrustManager());
        connection.addIRCEventListener(this);

        for (String channel : settings.channels)
            channels.add(channel);

        tryConnect();

        bs.addListener(new BuildServerAdapter() {
            @Override
            public void serverShutdown() {
                serverShutdown = true;
                connectTimer.cancel();

                if (connection.isConnected()) {
                    connection.doQuit("TeamCity Server shutting down...");
                    connection.close();
                }
            }
        });
    }

    public void joinChannel(String channel) {
        synchronized (channels) {
            if (channels.contains(channel))
                return;

            channels.add(channel);
            if (connection.isConnected())
                connection.doJoin(channel);
        }
    }

    public void sendToAllChannels(String message) {
        for(String channel : channels) {
            connection.doPrivmsg(channel, message);
        }
    }

    public void sendPrivMessage(String nickname, String message) {
        if (!connection.isConnected())
            return;

        connection.doPrivmsg(nickname, message);
    }

    public void sendPrivMessage(Set<String> nicknames, String message) {
        if (!connection.isConnected())
            return;

        for (String nickname : nicknames) {
            sendPrivMessage(nickname, message);
        }
    }

    private void tryConnect() {
        if (serverShutdown)
            return;

        try {
            connection.connect();
            LOG.info("Connected to IRC server");
        } catch (Exception ex) {
            // TODO add logging
            LOG.error("Failed to connect to IRC server", ex);
            connectTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    tryConnect();
                }
            }, 5000); // try again in 5secs
        }
    }

    @Override
    public void onDisconnected() {
        if (!serverShutdown) {
            tryConnect();
        }
    }

    @Override
    public void onError(String msg) {
        LOG.warn("IRC Error: " + msg);
    }

    @Override
    public void onError(int error, String msg) {
        LOG.warn("IRC Error: " + error + " msg: " + msg);
        if (error == 433) {
            currentNickname += "_";
            connection.doNick(currentNickname);
        }
    }

    @Override
    public void onInvite(String chan, IRCUser user, String passiveNick) {
        joinChannel(chan);
    }

    @Override
    public void onJoin(String arg0, IRCUser arg1) {
    }

    @Override
    public void onKick(String arg0, IRCUser arg1, String arg2, String arg3) {
    }

    @Override
    public void onMode(String arg0, IRCUser arg1, IRCModeParser arg2) {
    }

    @Override
    public void onMode(IRCUser arg0, String arg1, String arg2) {
    }

    @Override
    public void onNick(IRCUser arg0, String arg1) {
    }

    @Override
    public void onNotice(String arg0, IRCUser arg1, String arg2) {
    }

    @Override
    public void onPart(String arg0, IRCUser arg1, String arg2) {
    }

    @Override
    public void onPing(String arg0) {
    }

    @Override
    public void onPrivmsg(String target, IRCUser user, String message) {
    }

    @Override
    public void onQuit(IRCUser arg0, String arg1) {
    }

    @Override
    public void onRegistered() {

        LOG.info("Joining channels");
        for(String channel : channels) {
            connection.doJoin(channel);
        }
    }

    @Override
    public void onReply(int arg0, String arg1, String arg2) {
    }

    @Override
    public void onTopic(String arg0, IRCUser arg1, String arg2) {
    }

    @Override
    public void unknown(String arg0, String arg1, String arg2, String arg3) {
    }

    public void quit(String msg) {
        if(connection.isConnected()) {
            connection.doQuit(msg);
        }
    }

}
