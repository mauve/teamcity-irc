Install the plugin and stick the following in main-config.xml:

  <irc>
    <connection port="12345" ssl="true">irc.example.com</connection>
    <nickname>tcbot</nickname>
    <username>tcbot</username>
    <password>sekrit</password>
    <channels>
      <channel>#builds</channel>
      <channel projects="fo.* bar">#foo</channel>
    </channels>
  </irc>

