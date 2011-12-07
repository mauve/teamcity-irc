package se.olenfalk.teamcity;

import javax.servlet.http.HttpServletRequest;

import jetbrains.buildServer.web.openapi.SimplePageExtension;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PlaceId;

public class TCGrowlSettingsExtension extends SimplePageExtension {
    
    public TCGrowlSettingsExtension(PagePlaces pagePlaces) {    
        super(pagePlaces);        
        setIncludeUrl("tcgrowlSettings.jsp");
        setPlaceId(PlaceId.NOTIFIER_SETTINGS_FRAGMENT);
        setPluginName("tcgrowl");
        register();
    }
    
    public boolean isAvailable(HttpServletRequest request) {
        return super.isAvailable(request);
    }    
}