package org.jdownloader.captcha.v2.solver;

import org.appwork.storage.config.ConfigInterface;
import org.appwork.storage.config.annotations.AboutConfig;
import org.appwork.storage.config.annotations.DefaultBooleanValue;
import org.appwork.storage.config.annotations.DefaultIntValue;
import org.appwork.storage.config.annotations.DescriptionForConfigEntry;
import org.appwork.storage.config.annotations.SpinnerValidator;

public interface Captcha9kwSettings extends ConfigInterface {
    @AboutConfig
    @DescriptionForConfigEntry("Your (User) ApiKey from 9kw.eu")
    String getApiKey();

    void setApiKey(String jser);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Active the 9kw.eu service")
    boolean isEnabled();

    void setEnabled(boolean b);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Activate the Mouse Captchas")
    boolean ismouse();

    void setmouse(boolean b);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Confirm option for captchas (Cost +6)")
    boolean isconfirm();

    void setconfirm(boolean b);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Confirm option for mouse captchas (Cost +6)")
    boolean ismouseconfirm();

    void setmouseconfirm(boolean b);

    @AboutConfig
    @DefaultIntValue(0)
    @SpinnerValidator(min = 0, max = 10)
    @DescriptionForConfigEntry("More priority for captchas (Cost +1-10)")
    int getprio();

    void setprio(int seconds);

    @AboutConfig
    @DefaultIntValue(0)
    @SpinnerValidator(min = 0, max = 9999)
    @DescriptionForConfigEntry("Max. Captchas per hour")
    int gethour();

    void sethour(int seconds);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Only https requests to 9kw.eu")
    boolean ishttps();

    void sethttps(boolean b);

    @AboutConfig
    @DescriptionForConfigEntry("Captcha whitelist for hoster")
    String getwhitelist();

    void setwhitelist(String jser);

    @AboutConfig
    @DescriptionForConfigEntry("Captcha blacklist for hoster")
    String getblacklist();

    void setblacklist(String jser);

    @AboutConfig
    @DefaultBooleanValue(true)
    @DescriptionForConfigEntry("Activate the Captcha Feedback")
    boolean isfeedback();

    void setfeedback(boolean b);
}
