package se.olenfalk.teamcity.irc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SRunningBuild;

import org.schwering.irc.lib.IRCEventListener;
import org.schwering.irc.lib.IRCModeParser;
import org.schwering.irc.lib.IRCUser;
import org.schwering.irc.lib.ssl.SSLDefaultTrustManager;
import org.schwering.irc.lib.ssl.SSLIRCConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class IrcConnection implements IRCEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(IrcConnection.class);

    private IrcSettings settings;
    private SSLIRCConnection connection;
    private boolean serverShutdown = false;
    private String currentNickname = "teamcity";
    private Set<String> channels = new HashSet<String>();
    private Timer connectTimer = new Timer();
    private SBuildServer server;

    public IrcConnection(SBuildServer bs, IrcSettings is) {
        this.server = bs;
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

    private void reply(String target, IRCUser user, String message) {
        if(target.equals(currentNickname)) {
            // private message, reply in private
            connection.doPrivmsg(user.getNick(), message);
            LOG.info("> " + message + ", " + user.getNick());
        } else {
            connection.doPrivmsg(target, user.getNick() + ":" + message);
            LOG.info("> " + user.getNick() + ":" + message + ", " + target);
        }
    }

    @Override
    public void onPrivmsg(String target, IRCUser user, String message) {
        LOG.info("< " + message + ", " + target + ", " + user.getNick());

        String prefix = currentNickname + ":";

        if(message.startsWith(prefix)) {
            message = message.substring(prefix.length()).trim();
        } else if(target.equals(currentNickname)) {
            // private message
        } else {
            // ignore
            return;
        }

        // TODO handle quoted arguments
        String[] args = message.split("\\s+");

        List<String> reply = new ArrayList<String>();
        if("status".equals(message)) {
            if(!server.getRunningBuilds().isEmpty()) {
                reply.add("Running builds:");
                for(SRunningBuild build : server.getRunningBuilds()) {
                    reply.add(" - " + Util.getFullName(build));
                }
            } else {
                reply.add("No running builds");
            }

            if(!server.getQueue().isQueueEmpty()) {
                reply.add(server.getQueue().getNumberOfItems() + " queued builds");
            } else {
                reply.add("No queued builds");
            }
        } else if(message.startsWith("build ")) {
            if(args.length > 2) {
                String projectName  = args[1];
                String typeName     = args[2];
                SProject project = server.getProjectManager().findProjectByName(projectName);

                if(project != null) {
                    SBuildType buildType = project.findBuildTypeByName(typeName);
                    if(buildType != null) {
                        buildType.addToQueue(user.getNick());
                        reply.add("Build queued");
                    } else {
                        reply.add("Unknown build type");
                    }
                } else {
                    reply.add("Unknown project");
                }
            } else {
                reply.add("Missing parameters");
            }
        } else if(message.startsWith("show ")) {
            if(args.length > 1) {
                String projectName  = args[1];
                SProject project = server.getProjectManager().findProjectByName(projectName);
                if(project != null) {
                    for(SBuildType buildType : project.getBuildTypes()) {
                        reply.add(buildType.getFullName() + " - " + buildType.getStatus());
                    }
                } else {
                    reply.add("Unknown project");
                }
            }
        } else {
            reply.add("I understand these commands");
            reply.add("  build <project name> <build type name>");
            reply.add("         Start a build");
            reply.add("  help");
            reply.add("         Show this message");
            reply.add("  show <project name>");
            reply.add("         Show the status of the project");
            reply.add("  status");
            reply.add("         Show the running and queued builds");
        }

        for(String line : reply) {
            reply(target, user, line);
        }

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
