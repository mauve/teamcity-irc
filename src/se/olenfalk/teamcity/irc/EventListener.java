package se.olenfalk.teamcity.irc;

import java.util.List;
import java.util.Set;

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.responsibility.TestNameResponsibilityEntry;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.comments.Comment;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.util.EventDispatcher;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventListener extends BuildServerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger("IRC_NOTIFIER");

    private Connection connection;

    public static final String APP_NAME = "TeamCity";

    /**
     *
     */
    public EventListener(@NotNull EventDispatcher<BuildServerListener> dispatcher) {
        LOG.info("Registering EventListener with " + dispatcher);

        dispatcher.addListener(this);
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        LOG.info("Connection provided to IRC notifier");
        this.connection = connection;
    }

    private String formatRunningBuild(SRunningBuild srb, String state) {
        String msg = "Build " + srb.getFullName() + " " + srb.getBuildNumber() + " " + state;

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

    private String formatBuildType(SBuildType build, String what) {
        String msg = "Build type " + build.getFullName() + " " + what;
        return msg;
    }

    private String formationTestResponsibilityEntry(TestNameResponsibilityEntry test1,
            TestNameResponsibilityEntry test2, SProject project, String what) {
        String msg = "Tests " + test1.getTestName().getAsString() + " and " + test2.getTestName().getAsString()
                + " in project " + project.getName() + " " + what;
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
        LOG.warn("Build started " + srb.getBuildId());
        if(srb.getBuildStatus() == Status.ERROR || srb.getBuildStatus() == Status.FAILURE) {
            doNotifications(formatRunningBuild(srb, "failed"), null);
        } else {
            doNotifications(formatRunningBuild(srb, "succeeded"), null);
        }
    }

    @Override
    public void buildStarted(SRunningBuild srb) {
        LOG.warn("Build started " + srb.getBuildId());
        doNotifications(formatRunningBuild(srb, "started"), null);
    }
}
