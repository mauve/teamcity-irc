package se.olenfalk.teamcity.irc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import jetbrains.buildServer.serverSide.SProject;

import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;

public class IrcSettings {

    public static class Channel {

        private String name;
        private String projects;
        private List<Pattern> patterns;

        public Channel(String name, String projects) {
            this.name = name;
            this.projects = projects;

            this.patterns = new ArrayList<Pattern>();
            if(projects != null) {
                for(String project : Arrays.asList(projects.split("\\s+"))) {
                    this.patterns.add(Pattern.compile(project, Pattern.CASE_INSENSITIVE));
                }
            }
        }

        public String getName() {
            return name;
        }

        public String getProjects() {
            return projects;
        }

        public boolean interestedIn(SProject project) {
            if(patterns.isEmpty()) {
                // no patterns, interested in everything
                return true;
            }

            for(Pattern pattern : patterns) {
                if(pattern.matcher(project.getName()).matches()) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;

            Channel other = (Channel) obj;
            return other.name.equals(name);
        }


    }

    private static final String IRC = "irc";
    private static final String SERVER_CONN = "connection";
    private static final String PORT = "port";
    private static final String SSL = "ssl";
    private static final String NICKNAME = "nickname";
    private static final String USERNAME = "username";
    private static final String REALNAME = "realname";
    private static final String PASSWORD = "password";
    private static final String CHANNELS = "channels";
    private static final String CHANNEL = "channel";

    private static String DEFAULT_REALNAME = "Teamcity IRC Plugin";
    private static int DEFAULT_PORT = 6667;
    private static boolean DEFAULT_SSL = false;

    public String hostname;
    public int port;
    public boolean useSsl;
    public String nickname;
    public String username;
    public String password;
    public String realname;
    public List<Channel> channels = new ArrayList<Channel>();

    public static IrcSettings loadFrom(Element element) {
        IrcSettings ircSettings = new IrcSettings();

        Element srvElement = element.getChild(IRC);
        if (srvElement == null){
            return null;
        }

        // read connection data
        Element srvConnection = srvElement.getChild(SERVER_CONN);
        if (srvConnection == null) {
            return null;
        }

        ircSettings.hostname = srvConnection.getText();
        if (ircSettings.hostname.length() == 0)
            return null;

        Attribute portAttr = srvConnection.getAttribute(PORT);
        Attribute sslAttr = srvConnection.getAttribute(SSL);

        try {
            if (portAttr == null) {
                ircSettings.port = DEFAULT_PORT;
            } else {
                ircSettings.port = portAttr.getIntValue();
            }

            if (sslAttr == null) {
                ircSettings.useSsl = DEFAULT_SSL;
            } else {
                ircSettings.useSsl = sslAttr.getBooleanValue();
            }
        } catch (DataConversionException e) {
            return null;
        }

        Element nickElement = srvElement.getChild(NICKNAME);
        Element passElement = srvElement.getChild(PASSWORD);
        Element userElement = srvElement.getChild(USERNAME);
        Element realElement = srvElement.getChild(REALNAME);
        if (nickElement == null || userElement == null) {
            return null;

        }

        ircSettings.nickname = nickElement.getTextTrim();
        ircSettings.username = userElement.getTextTrim();
        if (passElement != null)
            ircSettings.password = passElement.getTextTrim();
        else
            ircSettings.password = "";

        if (realElement != null)
            ircSettings.realname = passElement.getTextTrim();
        else
            ircSettings.realname = DEFAULT_REALNAME;

        Element channelsElement = srvElement.getChild(CHANNELS);
        if (channelsElement == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        List<Element> channelsElements = (List<Element>) channelsElement.getChildren(CHANNEL);
        for (Element channelElement : channelsElements) {
            String channel = channelElement.getTextTrim();
            if (channel.isEmpty()) {
                continue;
            }
            String projects = channelElement.getAttributeValue("projects");
            ircSettings.channels.add(new Channel(channel, projects));
        }

        if (ircSettings.channels.isEmpty()) {
            return null;
        }

        return ircSettings;
    }

    public void writeTo(Element element) {
        Element irc = new Element(IRC);
        Element connection = new Element(SERVER_CONN);
        connection.setAttribute(SSL, Boolean.toString(useSsl));
        connection.setAttribute(PORT, Integer.toString(port));
        connection.setText(hostname);
        irc.addContent(connection);

        irc.addContent(new Element(NICKNAME).setText(nickname));
        irc.addContent(new Element(USERNAME).setText(username));
        irc.addContent(new Element(REALNAME).setText(realname));

        irc.addContent(new Element(PASSWORD).setText(password));

        Element channels = new Element(CHANNELS);
        for(Channel channel : this.channels) {
            Element channelElm = new Element(CHANNEL);
            channelElm.setText(channel.getName());
            if(channel.getProjects() != null) channelElm.setAttribute("projects", channel.getProjects());

            channels.addContent(channelElm);
        }

        irc.addContent(channels);

        element.addContent(irc);
    }
}
