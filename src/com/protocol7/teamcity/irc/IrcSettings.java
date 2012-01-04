package com.protocol7.teamcity.irc;

import java.util.ArrayList;
import java.util.List;

import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jdom.Attribute;

public class IrcSettings {
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
    public List<String> channels = new ArrayList<String>();

    public static IrcSettings loadFrom(Element element) {
        IrcSettings ircSettings = new IrcSettings();

        Element srvElement = element.getChild(IRC);
        if (srvElement == null){
            System.out.println("Not server");
            return null;
        }

        // read connection data
        Element srvConnection = srvElement.getChild(SERVER_CONN);
        if (srvConnection == null) {
            System.out.println("Not connection");
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
            System.out.println("DataConversionException");
            return null;
        }

        Element nickElement = srvElement.getChild(NICKNAME);
        Element passElement = srvElement.getChild(PASSWORD);
        Element userElement = srvElement.getChild(USERNAME);
        Element realElement = srvElement.getChild(REALNAME);
        if (nickElement == null || userElement == null) {
            System.out.println("Not nickname");
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
            System.out.println("Not channels");
            return null;
        }

        @SuppressWarnings("unchecked")
        List<Element> channelsElements = (List<Element>) channelsElement.getChildren(CHANNEL);
        for (Element channelElement : channelsElements) {
            String channel = channelElement.getTextTrim();
            if (channel.isEmpty())
                continue;
            ircSettings.channels.add(channel);
        }

        if (ircSettings.channels.isEmpty()) {
            System.out.println("Not channel");
            return null;
        }

        return ircSettings;
    }

    public void writeTo(Element element) {

    }
}
