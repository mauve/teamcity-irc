package com.protocol7.teamcity.irc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import com.protocol7.teamcity.irc.IrcSettings.Channel;


public class IrcConnection implements IRCEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(IrcConnection.class);

    private static final Pattern ARGUMENTS_PATTERN = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");

    private IrcSettings settings;
    private SSLIRCConnection connection;
    private boolean serverShutdown = false;
    private String currentNickname = "teamcity";
    private Set<Channel> channels = new HashSet<Channel>();
    private Timer connectTimer = new Timer();
    private SBuildServer server;

    public IrcConnection(SBuildServer bs, IrcSettings is) {
        this.server = bs;
        settings = is;
        currentNickname = settings.nickname;

        connection = createConnection(settings);
        connection.addTrustManager(new SSLDefaultTrustManager());
        connection.addIRCEventListener(this);

        for (Channel channel : settings.channels)
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

    private SSLIRCConnection createConnection(IrcSettings settings) {
        return new SSLIRCConnection(settings.hostname,
                new int[] { settings.port },
                settings.password,
                settings.nickname,
                settings.username,
                settings.realname);
    }

    public void sendToAllChannels(String message, SProject project) {
        for(Channel channel : channels) {
            if(channel.interestedIn(project)) {
                connection.doPrivmsg(channel.getName(), message);
            }
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
            LOG.error("Failed to connect to IRC server", ex);

            // recreate the connection
            try {
                connection.close();
            } catch(Exception e) {
                // ignore
            }
            connection = createConnection(settings);

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

    private void reply(String target, IRCUser user, String message) {
        String to;
        if(target.equals(currentNickname)) {
            // private message, reply in private
            to = user.getNick();
        } else {
            // in-channel message, reply to user on channel
            message = user.getNick() + ": " + message;
            to = target;
        }
        connection.doPrivmsg(to, message);
        LOG.info("> " + message + ", " + to);
    }

    private List<String> parseCommand(String command) {
        Matcher matcher = ARGUMENTS_PATTERN.matcher(command);

        List<String> arguments = new ArrayList<String>();
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                // Add double-quoted string without the quotes
                arguments.add(matcher.group(1));
            } else if (matcher.group(2) != null) {
                // Add single-quoted string without the quotes
                arguments.add(matcher.group(2));
            } else {
                // Add unquoted word
                arguments.add(matcher.group());
            }
        }
        return arguments;
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

        List<String> args = parseCommand(message);

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
            if(args.size() > 2) {
                String projectName  = args.get(1);
                String typeName     = args.get(2);
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
            if(args.size() > 1) {
                String projectName  = args.get(1);
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
            if(!"help".equals(message)) {
                reply.add("What?");
            }
            reply.add("I understand these commands");
            reply.add("    build <project name> <build type name>");
            reply.add("        Start a build");
            reply.add("    help");
            reply.add("        Show this message");
            reply.add("    show <project name>");
            reply.add("        Show the status of the project");
            reply.add("    status");
            reply.add("        Show the running and queued builds");
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
        for(Channel channel : channels) {
            connection.doJoin(channel.getName());
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

    @Override
    public void onInvite(String chan, IRCUser user, String passiveNick) {
        connection.doJoin(chan);
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

    public void quit(String msg) {
        if(connection.isConnected()) {
            connection.doQuit(msg);
        }
    }

}
