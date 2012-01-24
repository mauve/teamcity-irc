package se.olenfalk.teamcity.irc;

import jetbrains.buildServer.serverSide.SRunningBuild;

public class Util {

    public static String getFullName(SRunningBuild srb) {
        return srb.getFullName() + " " + srb.getBuildNumber(); //.replace("???", "");
    }
}
