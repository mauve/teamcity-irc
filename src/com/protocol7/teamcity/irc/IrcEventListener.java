package com.protocol7.teamcity.irc;

import java.util.ArrayList;
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

    private List<String> formatRunningBuild(SRunningBuild srb, String state) {
        List<String> messages = new ArrayList<String>();

        String msg = "Build " + Util.getFullName(srb) + " " + state;

        Comment comment = srb.getBuildComment();
        if (comment != null) {
            msg += " (" + comment.getUser().getName() + ": " + comment.getComment() + ")";
        }

        msg += " (on agent: " + srb.getAgentName() + ")";
        messages.add(msg);

        List<BuildProblem> problems = srb.getBuildProblems();
        if (!problems.isEmpty()) {
            messages.add("Build Problems:");
            for (BuildProblem buildProblem : problems) {
                messages.add("    - " + buildProblem.getStringRepresentation());
            }
        }
        return messages;
    }

    private void doNotifications(List<String> messages, SProject project) {
        if(connection == null) {
            return;
        }

        for(String message : messages) {
            connection.sendToAllChannels(message, project);
        }
    }

    @Override
    public void buildFinished(SRunningBuild srb) {
        LOG.info("Build finished " + Util.getFullName(srb));
        if(srb.getBuildStatus() == Status.ERROR || srb.getBuildStatus() == Status.FAILURE) {
            doNotifications(formatRunningBuild(srb, "failed"), getProject(srb));
        } else {
            doNotifications(formatRunningBuild(srb, "succeeded"), getProject(srb));
        }
    }

    @Override
    public void buildStarted(SRunningBuild srb) {
        LOG.info("Build started " + Util.getFullName(srb));
        //doNotifications(formatRunningBuild(srb, "started"), getProject(srb));
    }

    private SProject getProject(SRunningBuild srb) {
        return server.getProjectManager().findProjectById(srb.getProjectId());
    }
}
