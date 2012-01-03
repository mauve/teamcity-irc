package se.olenfalk.teamcity.irc;

import java.util.List;
import java.util.Set;

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.comments.Comment;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.EventDispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IrcEventListener extends BuildServerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(IrcEventListener.class);

    private IrcConnection connection;
    private SBuildServer server;

    public static final String APP_NAME = "TeamCity";

    /**
     *
     */
    public IrcEventListener(SBuildServer server, EventDispatcher<BuildServerListener> dispatcher) {
        this.server = server;

        LOG.info("Registering EventListener with " + dispatcher);
        dispatcher.addListener(this);
    }

    public IrcConnection getConnection() {
        return connection;
    }

    public void setConnection(IrcConnection connection) {
        LOG.info("Connection provided to IRC notifier");
        this.connection = connection;
    }

    private String formatRunningBuild(SRunningBuild srb, String state) {
        String msg = "Build " + Util.getFullName(srb) + " " + state;

        Comment comment = srb.getBuildComment();
        if (comment != null) {
            msg += " (" + comment.getUser().getName() + ": " + comment.getComment() + ")";
        }

        msg += " (on agent: " + srb.getAgentName() + ")";

        List<BuildProblem> problems = srb.getBuildProblems();
        if (!problems.isEmpty()) {
            msg += "\nBuild Problems:\n";
            for (BuildProblem buildProblem : problems) {
                msg += "    - " + buildProblem.getStringRepresentation();
            }
        }
        return msg;
    }

    private void doNotifications(String message, Set<SUser> users) {
        if(connection == null) {
            return;
        }

        connection.sendToAllChannels(message);
    }

    @Override
    public void buildFinished(SRunningBuild srb) {
        LOG.info("Build finished " + Util.getFullName(srb));
        if(srb.getBuildStatus() == Status.ERROR || srb.getBuildStatus() == Status.FAILURE) {
            doNotifications(formatRunningBuild(srb, "failed"), null);
        } else {
            doNotifications(formatRunningBuild(srb, "succeeded"), null);
        }
    }

    @Override
    public void buildStarted(SRunningBuild srb) {
        LOG.info("Build started " + Util.getFullName(srb));
        doNotifications(formatRunningBuild(srb, "started"), null);

        SProject project = server.getProjectManager().findProjectById(srb.getProjectId());
        System.out.println(project.getParameters());
    }
}
