<%@ include file="/include.jsp" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<script type="text/javascript">
    function sendTest() {
    
        var nick = $('properties[ircNotifier.Nickname].value');
        
        var gm = $('ircTestMessage').value;
        if(!gm || gm.length ==0) {
            return;
        }
    
        BS.ajaxRequest($('ircTestForm').action, {
            parameters: 'nickname='+ nick.value,
            onComplete: function(transport) {
              if (transport.responseXML) {
                  $('ircTestForm').refresh();
              }             
            }
        });
        return false;        
    }
</script>

<bs:refreshable containerId="ircTestForm" pageUrl="${pageUrl}">
<bs:messages key="ircTestMessage"/>

<form action="/ircSettings.html" method="post" id="ircTestForm">
Send test message to Irc server: <input id="ircTestMessage" name="ircTestMessage" type="text" />  <input type="button" name="Test" value="Test" onclick="return sendTest();"/>
</form>
</bs:refreshable>