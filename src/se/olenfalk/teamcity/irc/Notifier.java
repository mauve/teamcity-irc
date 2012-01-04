package se.olenfalk.teamcity.irc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import jetbrains.buildServer.notification.Notificator;
import jetbrains.buildServer.notification.NotificatorRegistry;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.responsibility.TestNameResponsibilityEntry;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.UserPropertyInfo;
import jetbrains.buildServer.serverSide.comments.Comment;
import jetbrains.buildServer.serverSide.mute.MuteInfo;
import jetbrains.buildServer.serverSide.problems.BuildProblem;
import jetbrains.buildServer.tests.TestName;
import jetbrains.buildServer.users.NotificatorPropertyKey;
import jetbrains.buildServer.users.PropertyKey;
import jetbrains.buildServer.users.SUser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Notifier implements Notificator {

    private static final Logger LOG = LoggerFactory.getLogger("IRC_NOTIFIER");

    private static final String TYPE = "ircNotifier";

    private ArrayList<UserPropertyInfo> props;
    private static final String IRC_NICKNAME = "ircNotifier.Nickname";
    private static final PropertyKey NICKNAME = new NotificatorPropertyKey(TYPE, IRC_NICKNAME);

    private IrcConnection connection;

    public static final String APP_NAME = "TeamCity";

    /**
     *
     */
    public Notifier(NotificatorRegistry nr) {
        LOG.info("Registering Notifier...");
        props = new ArrayList<UserPropertyInfo>();
        //props.add(new UserPropertyInfo(IRC_NICKNAME, "IRC Nickname"));
        //nr.register(this, props);
        nr.register(this);
    }

    public IrcConnection getConnection() {
        return connection;
    }

    public void setConnection(IrcConnection connection) {
        LOG.info("Connection provided to IRC notifier");
        this.connection = connection;
    }



    public String getDisplayName() {
        return "IRC Notifier";
    }

    public String getNotificatorType() {
        return TYPE;
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

    /*
     *
     * Notificator
     */

    public void notifyBuildFailed(SRunningBuild srb, Set<SUser> users) {
        LOG.info("notifyBuildFailed");
        doNotifications(formatRunningBuild(srb, "failed"), users);
    }

    public void notifyBuildFailing(SRunningBuild srb, Set<SUser> users) {
        LOG.info("notifyBuildFailing");
        doNotifications(formatRunningBuild(srb, "failing"), users);
    }

    public void notifyBuildProbablyHanging(SRunningBuild srb, Set<SUser> users) {
        LOG.info("notifyBuildProbablyHanging");
        doNotifications(formatRunningBuild(srb, "probably hanging"), users);
    }

    public void notifyBuildStarted(SRunningBuild srb, Set<SUser> users) {
        LOG.info("notifyBuildStarted");
        doNotifications(formatRunningBuild(srb, "started"), users);
    }

    public void notifyBuildSuccessful(SRunningBuild srb, Set<SUser> users) {
        LOG.info("notifyBuildSuccessful");
        doNotifications(formatRunningBuild(srb, "succeeded"), users);
    }

    public void notifyResponsibleChanged(SBuildType sbt, Set<SUser> users) {
        LOG.info("notifyResponsibleChanged");
        doNotifications("Responsible user changed...", users);
    }

    @Override
    public void notifyLabelingFailed(jetbrains.buildServer.Build build, jetbrains.buildServer.vcs.VcsRoot root,
            java.lang.Throwable t, java.util.Set<jetbrains.buildServer.users.SUser> users) {
        LOG.info("notifyLabelingFailed");
        doNotifications("Labeling failed...", users);
    }

    @Override
    public void notifyBuildFailedToStart(SRunningBuild srb, Set<SUser> users) {
        LOG.info("notifyBuildFailedToStart");
        doNotifications(formatRunningBuild(srb, "failed to start"), users);
    }

    @Override
    public void notifyResponsibleAssigned(SBuildType build, Set<SUser> users) {
        LOG.info("notifyResponsibleAssigned (Builds)");
        doNotifications(formatBuildType(build, "was assigned to you."), users);
    }

    @Override
    public void notifyResponsibleAssigned(TestNameResponsibilityEntry test1, TestNameResponsibilityEntry test2,
            SProject project, Set<SUser> users) {
        LOG.info("notifyResponsibleAssigned (Tests)");
        doNotifications(formationTestResponsibilityEntry(test1, test2, project, " were assigned to you"), users);
    }

    @Override
    public void notifyResponsibleAssigned(Collection<TestName> arg0, ResponsibilityEntry arg1, SProject arg2,
            Set<SUser> arg3) {

    }

    @Override
    public void notifyResponsibleChanged(TestNameResponsibilityEntry arg0, TestNameResponsibilityEntry arg1,
            SProject arg2, Set<SUser> arg3) {

    }

    @Override
    public void notifyResponsibleChanged(Collection<TestName> arg0, ResponsibilityEntry arg1, SProject arg2,
            Set<SUser> arg3) {

    }

    @Override
    public void notifyTestsMuted(Collection<STest> arg0, MuteInfo arg1, Set<SUser> arg2) {

    }

    @Override
    public void notifyTestsUnmuted(Collection<STest> arg0, MuteInfo arg1, Set<SUser> arg2) {

    }

    private void doNotifications(String message, Set<SUser> users) {
        if(connection == null) {
            return;
        }

        for (SUser user : users) {
            LOG.info("notifying user: " + user.getUsername());
            String username = user.getUsername();
            String ircNickname = user.getPropertyValue(NICKNAME);

            if (ircNickname == null)
                ircNickname = username;

            connection.sendPrivMessage(ircNickname, message);
        }
    }
}
