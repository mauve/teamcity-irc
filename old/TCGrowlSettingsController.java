package se.olenfalk.teamcity;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.AjaxRequestProcessor;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.XmlResponseUtil;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.openapi.WebResourcesManager;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.version.ServerVersionHolder;
import jetbrains.buildServer.version.ServerVersionInfo;
import jetbrains.buildServer.util.PropertiesUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;


import com.binaryblizzard.growl.Growl;
import com.binaryblizzard.growl.GrowlException;
import com.binaryblizzard.growl.GrowlNotification;
import com.binaryblizzard.growl.GrowlRegistrations;


/**
 * Controller to handle test form on TCGrowl Settings page.
 * 
 * Based on the TC Profiler plugin controller. 
 *   http://www.jetbrains.net/confluence/display/TW/Server+Profiling
 * 
 * @author Nathanial Drake
 */
public class TCGrowlSettingsController extends BaseController {

    public TCGrowlSettingsController(final SBuildServer server, WebControllerManager manager,
                                     WebResourcesManager webResources) {
        super(server);
        manager.registerController("/tcgrowlSettings.html", this);

        //in TeamCity < 4.x EAP we need to register plugin resources automatically.
        final ServerVersionInfo serverVersionInfo = ServerVersionHolder.getVersion();
        if (serverVersionInfo.getDisplayVersionMajor()<4){
            webResources.addPluginResources("tcGrowl", "tcgrowl.jar");
        }
    }

    @Nullable
    protected ModelAndView doHandle(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
        new AjaxRequestProcessor().processRequest(request, response, new AjaxRequestProcessor.RequestHandler() {
            public void handleRequest(final HttpServletRequest request, final HttpServletResponse response, final Element xmlResponse) {
                try {
                    doAction(request);
                } catch (Exception e) {
                    Loggers.SERVER.warn(e);
                    ActionErrors errors = new ActionErrors();
                    errors.addError("tcgrowlProblem", getMessageWithNested(e));
                    errors.serialize(xmlResponse);
                }
            }
            });

            return null;
    }

    static private String getMessageWithNested(Throwable e) {
        String result = e.getMessage();
        Throwable cause = e.getCause();
        if (cause != null) {
            result += " Caused by: " + getMessageWithNested(cause);
        }
        return result;
    }

    private void doAction(final HttpServletRequest request) throws Exception {
        String growlServer = request.getParameter("growlServer");
        String growlPassword = request.getParameter("growlPasswd");
        String msg = request.getParameter("growlTestMessage");

        try {
            Growl g = new Growl();
            g.addGrowlHost(growlServer, growlPassword);
            GrowlRegistrations registrations = g.getRegistrations(IrcNotifier.APP_NAME);
            registrations.registerNotification(IrcNotifier.APP_NAME, true);

            g.sendNotification(new GrowlNotification(IrcNotifier.APP_NAME, 
                IrcNotifier.APP_NAME, 
                msg, 
                IrcNotifier.APP_NAME, 
                false, 
                GrowlNotification.NORMAL));                                

            addSuccessMessage(request, "Test message sent.");
            
        } catch(GrowlException e) {
            addSuccessMessage(request, "Error sending the test message: " + e.getMessage());
            return;
        }


    }

    private void addSuccessMessage(HttpServletRequest request, String result) {
        if (result != null) {
            getOrCreateMessages(request).addMessage("tcgrowlMessage", result);
        }
    }
}
