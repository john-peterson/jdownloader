//jDownloader - Downloadmanager
//Copyright (C) 2012  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.hoster;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.parser.html.HTMLParser;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;

import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "fileplanet.com.ua", "fileplaneta.com" }, urls = { "BLABLAUNUSED-REGEXZEJFRIFHJZOJNEODCKREOHKUJP", "https?://(www\\.)?(fileplanet\\.com\\.ua|fileplaneta\\.com)/[a-z0-9]{12}" }, flags = { 0, 2 })
public class FilePlanetaCom extends PluginForHost {

    private String               correctedBR         = "";
    private static final String  PASSWORDTEXT        = "(<br><b>Password:</b> <input|<br><b>Passwort:</b> <input)";
    private static final String  COOKIE_HOST         = "http://fileplaneta.com";
    // domain names used within download links.
    private static final String  DOMAINS             = "(fileplaneta\\.com)";
    private static final String  MAINTENANCE         = ">This server is in maintenance mode";
    private static final String  MAINTENANCEUSERTEXT = "This server is under Maintenance";
    private static final String  ALLWAIT_SHORT       = "Waiting till new downloads can be started";
    private static AtomicInteger maxPrem             = new AtomicInteger(1);
    private static Object        LOCK                = new Object();

    // XfileSharingProBasic Version 2.5.2.2, added special download handling
    // (ajax) & special FNF handling
    // captchatype: keyCaptcha

    @Override
    public String getAGBLink() {
        return COOKIE_HOST + "/tos.html";
    }

    public void correctDownloadLink(DownloadLink link) {
        link.setUrlDownload("http://fileplaneta.com/" + new Regex(link.getDownloadURL(), "([a-z0-9]{12})$").getMatch(0));
    }

    public FilePlanetaCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium(COOKIE_HOST + "/premium.html");
    }

    // do not add @Override here to keep 0.* compatibility
    public boolean hasAutoCaptcha() {
        return false;
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        if (acc == null) {
            /* no account, yes we can expect captcha */
            return true;
        }
        if (Boolean.TRUE.equals(acc.getBooleanProperty("free"))) {
            /* free accounts also have captchas */
            return true;
        }
        return false;
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws Exception {
        this.setBrowserExclusive();
        br.setFollowRedirects(false);
        prepBrowser(br);
        getPage(link.getDownloadURL());
        if (new Regex(correctedBR, Pattern.compile("(No such file|>File Not Found<|>The file was removed by|Reason (of|for) deletion:\n)", Pattern.CASE_INSENSITIVE)).matches() || correctedBR.contains("Извините, мы вынуждены на неопределённое время прекратить обслуживание зарубежных посетителей в бесплатном режиме")) throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        if (correctedBR.contains(MAINTENANCE)) {
            link.getLinkStatus().setStatusText(JDL.L("plugins.hoster.xfilesharingprobasic.undermaintenance", MAINTENANCEUSERTEXT));
            return AvailableStatus.TRUE;
        }
        String filename = new Regex(correctedBR, "You have requested.*?https?://(www\\.)?" + COOKIE_HOST.replaceAll("https?://", "") + "/[A-Za-z0-9]{12}/(.*?)</font>").getMatch(1);
        if (filename == null) {
            filename = new Regex(correctedBR, "fname\"( type=\"hidden\")? value=\"(.*?)\"").getMatch(1);
            if (filename == null) {
                filename = new Regex(correctedBR, "<h2>Download File(.*?)</h2>").getMatch(0);
            }
        }
        String filesize = new Regex(correctedBR, "\\(([0-9]+ bytes)\\)").getMatch(0);
        if (filesize == null) {
            filesize = new Regex(correctedBR, "</font>[ ]+\\(([^<>\"\\'/]+)\\)(.*?)</font>").getMatch(0);
            if (filesize == null) filesize = new Regex(correctedBR, "Size:[\t\n\r ]+</td>[\t\n\r ]+<td>([^<>\"/]*?)</td>").getMatch(0);
        }
        if (filename == null || filename.equals("")) {
            if (correctedBR.contains("You have reached the download-limit")) {
                logger.warning("Waittime detected, please reconnect to make the linkchecker work!");
                return AvailableStatus.UNCHECKABLE;
            }
            logger.warning("The filename equals null, throwing \"plugin defect\" now...");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        filename = filename.replaceAll("(</b>|<b>|\\.html)", "");
        link.setFinalFileName(filename.trim());
        if (filesize != null && !filesize.equals("")) link.setDownloadSize(SizeFormatter.getSize(filesize));
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        doFree(downloadLink, true, 1, "freelink");
    }

    public void doFree(DownloadLink downloadLink, boolean resumable, int maxchunks, String directlinkproperty) throws Exception, PluginException {
        String passCode = null;
        String fid = new Regex(downloadLink.getDownloadURL(), COOKIE_HOST.replaceAll("https?://", "") + "/" + "([A-Za-z0-9]{12})").getMatch(0);
        String md5hash = new Regex(correctedBR, "<b>MD5.*?</b>.*?nowrap>(.*?)<").getMatch(0);
        if (md5hash != null) {
            md5hash = md5hash.trim();
            logger.info("Found md5hash: " + md5hash);
            downloadLink.setMD5Hash(md5hash);
        }

        String dllink = checkDirectLink(downloadLink, directlinkproperty);
        if (dllink != null) {
            try {
                Browser br2 = br.cloneBrowser();
                URLConnectionAdapter con = br2.openGetConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(directlinkproperty, Property.NULL);
                    dllink = null;
                }
                con.disconnect();
            } catch (Exception e) {
                downloadLink.setProperty(directlinkproperty, Property.NULL);
                dllink = null;
            }
        }

        /**
         * Video links can already be found here, if a link is found here we can skip wait times and captchas
         */
        if (dllink == null) {
            checkErrors(downloadLink, false, passCode);
            if (correctedBR.contains("\"download1\"")) {
                postPage(downloadLink.getDownloadURL(), "op=download1&usr_login=&id=" + fid + "&fname=" + Encoding.urlEncode(downloadLink.getName()) + "&referer=&method_free=Free+Download");
                checkErrors(downloadLink, false, passCode);
            }
            dllink = getDllink();
        }
        if (dllink == null) {
            Form dlForm = br.getFormbyProperty("name", "F1");
            if (dlForm == null) throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            final long timeBefore = System.currentTimeMillis();
            boolean password = false;
            boolean skipWaittime = false;
            if (new Regex(correctedBR, PASSWORDTEXT).matches()) {
                password = true;
                logger.info("The downloadlink seems to be password protected.");
            }

            /* Captcha START */
            if (correctedBR.contains(";background:#ccc;text-align")) {
                logger.info("Detected captcha method \"plaintext captchas\" for this host");
                final String code = get4DigitCode();
                dlForm.put("code", code.toString());
                logger.info("Put captchacode " + code.toString() + " obtained by captcha metod \"plaintext captchas\" in the form.");
            } else if (correctedBR.contains("/captchas/")) {
                logger.info("Detected captcha method \"Standard captcha\"");
                String[] sitelinks = HTMLParser.getHttpLinks(br.toString(), null);
                String captchaurl = null;
                if (sitelinks == null || sitelinks.length == 0) {
                    logger.warning("Standard captcha captchahandling broken!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                for (String link : sitelinks) {
                    if (link.contains("/captchas/")) {
                        captchaurl = link;
                        break;
                    }
                }
                if (captchaurl == null) {
                    logger.warning("Standard captcha captchahandling broken!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                String code = getCaptchaCode("xfilesharingprobasic", captchaurl, downloadLink);
                dlForm.put("code", code);
                logger.info("Put captchacode " + code + " obtained by captcha metod \"Standard captcha\" in the form.");
            } else if (new Regex(correctedBR, "(api\\.recaptcha\\.net|google\\.com/recaptcha/api/)").matches()) {
                logger.info("Detected captcha method \"Re Captcha\"");
                PluginForHost recplug = JDUtilities.getPluginForHost("DirectHTTP");
                jd.plugins.hoster.DirectHTTP.Recaptcha rc = ((DirectHTTP) recplug).getReCaptcha(br);
                rc.setForm(dlForm);
                String id = this.br.getRegex("\\?k=([A-Za-z0-9%_\\+\\- ]+)\"").getMatch(0);
                rc.setId(id);
                rc.load();
                File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                String c = getCaptchaCode(cf, downloadLink);
                Form rcform = rc.getForm();
                rcform.put("recaptcha_challenge_field", rc.getChallenge());
                rcform.put("recaptcha_response_field", Encoding.urlEncode(c));
                logger.info("Put captchacode " + c + " obtained by captcha metod \"Re Captcha\" in the form and submitted it.");
                dlForm = rc.getForm();
                /** wait time is often skippable for reCaptcha handling */
                // skipWaittime = true;
            } else if (br.containsHTML("<\\!\\-\\- KeyCAPTCHA code")) {
                logger.info("Detected captcha method \"keycaptca\"");
                PluginForDecrypt keycplug = JDUtilities.getPluginForDecrypt("linkcrypt.ws");
                jd.plugins.decrypter.LnkCrptWs.KeyCaptcha kc = ((jd.plugins.decrypter.LnkCrptWs) keycplug).getKeyCaptcha(br);
                final String result = kc.showDialog(downloadLink.getDownloadURL());
                if (result != null && "CANCEL".equals(result)) { throw new PluginException(LinkStatus.ERROR_FATAL); }
                dlForm.put("capcode", result);
            }
            /* Captcha END */
            if (password) passCode = handlePassword(passCode, dlForm, downloadLink);
            if (!skipWaittime) waitTime(timeBefore, downloadLink);
            sendForm(dlForm);
            logger.info("Submitted DLForm");
            checkErrors(downloadLink, true, passCode);
            dllink = getDllink();
            if (dllink == null) {
                /** Unusual part for xfilesharing start */
                String s = br.getRegex("\\&s=([a-z0-9]+)\\&ajax=1").getMatch(0);
                if (s == null) s = br.getRegex("<script>console\\.log\\(\\'([a-z0-9]+)\\'\\)</script>").getMatch(0);
                if (s == null) {
                    logger.warning("s is null");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                waitTime(System.currentTimeMillis(), downloadLink);
                br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                br.getHeaders().put("Accept", "application/json, text/javascript, */*");
                br.getHeaders().put("Accept-Charset", null);
                br.setCookie(COOKIE_HOST, "download_referer", Encoding.urlEncode_light(br.getURL()));
                getPage("http://fileplaneta.com/?op=download2&id=" + fid + "&s=" + s + "&ajax=1");
                checkErrors(downloadLink, true, passCode);
                dllink = getDllink();
                /** Unusual part for xfilesharing end */
                if (dllink == null) {
                    logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            }
        }
        logger.info("Final downloadlink = " + dllink + " starting the download...");
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, resumable, maxchunks);
        if (dl.getConnection().getContentType().contains("html")) {
            logger.warning("The final dllink seems not to be a file!");
            br.followConnection();
            correctBR();
            checkServerErrors();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        downloadLink.setProperty(directlinkproperty, dllink);
        if (passCode != null) downloadLink.setProperty("pass", passCode);
        dl.startDownload();
    }

    private String get4DigitCode() throws PluginException {
        /** Captcha method by ManiacMansion */
        String[][] letters = new Regex(Encoding.htmlDecode(br.toString()), "<span style=\\'position:absolute;padding\\-left:(\\d+)px;padding\\-top:\\d+px;\\'>(\\d)</span>").getMatches();
        if (letters == null || letters.length == 0) {
            logger.warning("plaintext captchahandling broken!");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        SortedMap<Integer, String> capMap = new TreeMap<Integer, String>();
        for (String[] letter : letters) {
            capMap.put(Integer.parseInt(letter[0]), letter[1]);
        }
        StringBuilder code = new StringBuilder();
        for (String value : capMap.values()) {
            code.append(value);
        }
        return code.toString();
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 1;
    }

    private void getPage(String page) throws Exception {
        br.getPage(page);
        correctBR();
    }

    private void postPage(String page, String postdata) throws Exception {
        br.postPage(page, postdata);
        correctBR();
    }

    private void sendForm(Form form) throws Exception {
        br.submitForm(form);
        correctBR();
    }

    private String checkDirectLink(DownloadLink downloadLink, String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            try {
                Browser br2 = br.cloneBrowser();
                URLConnectionAdapter con = br2.openGetConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
                con.disconnect();
            } catch (Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            }
        }
        return dllink;
    }

    @Override
    public AccountInfo fetchAccountInfo(Account account) throws Exception {
        AccountInfo ai = new AccountInfo();
        /* reset maxPrem workaround on every fetchaccount info */
        maxPrem.set(1);
        try {
            login(account, true);
        } catch (PluginException e) {
            account.setValid(false);
            return ai;
        }
        String space = new Regex(correctedBR, "<td>Used space:</td>.*?<td.*?b>([0-9\\.]+) of [0-9\\.]+ (Mb|GB)</b>").getMatch(0);
        if (space != null) ai.setUsedSpace(space.trim() + " Mb");
        account.setValid(true);
        String availabletraffic = new Regex(correctedBR, "Traffic available.*?:</TD><TD><b>([^<>\"\\']+)</b>").getMatch(0);
        if (availabletraffic != null && !availabletraffic.contains("nlimited") && !availabletraffic.equalsIgnoreCase(" Mb")) {
            availabletraffic.trim();
            // need to set 0 traffic left, as getSize returns positive result, even when negative value supplied.
            if (!availabletraffic.startsWith("-")) {
                ai.setTrafficLeft(SizeFormatter.getSize(availabletraffic));
            } else {
                ai.setTrafficLeft(0);
            }
        } else {
            ai.setUnlimitedTraffic();
        }
        if (account.getBooleanProperty("nopremium")) {
            ai.setStatus("Registered (free) User");
            try {
                maxPrem.set(1);
                account.setMaxSimultanDownloads(1);
                account.setConcurrentUsePossible(false);
            } catch (final Throwable e) {
            }
        } else {
            String expire = new Regex(correctedBR, Pattern.compile("<td>Premium(\\-| )Account expires?:</td>.*?<td>(<b>)?(\\d{1,2} [A-Za-z]+ \\d{4})(</b>)?</td>", Pattern.CASE_INSENSITIVE)).getMatch(2);
            if (expire == null) {
                ai.setExpired(true);
                account.setValid(false);
                return ai;
            } else {
                expire = expire.replaceAll("(<b>|</b>)", "");
                ai.setValidUntil(TimeFormatter.getMilliSeconds(expire, "dd MMMM yyyy", null));
                try {
                    maxPrem.set(20);
                    account.setMaxSimultanDownloads(-1);
                    account.setConcurrentUsePossible(true);
                } catch (final Throwable e) {
                }
            }
            ai.setStatus("Premium User");
        }
        return ai;
    }

    @Override
    public void handlePremium(DownloadLink link, Account account) throws Exception {
        String passCode = null;
        requestFileInformation(link);
        login(account, false);
        br.setFollowRedirects(false);
        String dllink = null;
        if (account.getBooleanProperty("nopremium")) {
            getPage(link.getDownloadURL());
            doFree(link, true, 1, "freelink2");
        } else {
            dllink = checkDirectLink(link, "premlink");
            if (dllink == null) {
                getPage(link.getDownloadURL());
                dllink = getDllink();
                if (dllink == null) {
                    checkErrors(link, true, passCode);
                    Form dlform = br.getFormbyProperty("name", "F1");
                    if (dlform == null) throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    if (new Regex(correctedBR, PASSWORDTEXT).matches()) passCode = handlePassword(passCode, dlform, link);
                    sendForm(dlform);
                    dllink = getDllink();
                    checkErrors(link, true, passCode);
                }
            }
            if (dllink == null) {
                logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            logger.info("Final downloadlink = " + dllink + " starting the download...");
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 0);
            if (dl.getConnection().getContentType().contains("html")) {
                logger.warning("The final dllink seems not to be a file!");
                br.followConnection();
                correctBR();
                checkServerErrors();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            if (passCode != null) link.setProperty("pass", passCode);
            link.setProperty("premlink", dllink);
            dl.startDownload();
        }
    }

    @SuppressWarnings("unchecked")
    private void login(Account account, boolean force) throws Exception {
        synchronized (LOCK) {
            try {
                /** Load cookies */
                br.setCookiesExclusive(true);
                prepBrowser(br);
                final Object ret = account.getProperty("cookies", null);
                boolean acmatch = Encoding.urlEncode(account.getUser()).equals(account.getStringProperty("name", Encoding.urlEncode(account.getUser())));
                if (acmatch) acmatch = Encoding.urlEncode(account.getPass()).equals(account.getStringProperty("pass", Encoding.urlEncode(account.getPass())));
                if (acmatch && ret != null && ret instanceof HashMap<?, ?> && !force) {
                    final HashMap<String, String> cookies = (HashMap<String, String>) ret;
                    if (account.isValid()) {
                        for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                            final String key = cookieEntry.getKey();
                            final String value = cookieEntry.getValue();
                            this.br.setCookie(COOKIE_HOST, key, value);
                        }
                        return;
                    }
                }
                getPage(COOKIE_HOST + "/login.html");
                Form loginform = br.getForm(0);
                if (loginform == null) throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                correctBR();
                if (correctedBR.contains(";background:#ccc;text-align")) {
                    final String code = get4DigitCode();
                    loginform.put("code", code);
                }
                loginform.put("login", Encoding.urlEncode(account.getUser()));
                loginform.put("password", Encoding.urlEncode(account.getPass()));
                sendForm(loginform);
                if (br.getCookie(COOKIE_HOST, "login") == null || br.getCookie(COOKIE_HOST, "xfss") == null) throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                getPage(COOKIE_HOST + "/?op=my_account");
                if (!new Regex(correctedBR, "(?i)(Premium(\\-| )Account expire|Upgrade to premium|>Renew premium<)").matches()) throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                if (!new Regex(correctedBR, "(?i)(Premium(\\-| )Account expire|>Renew premium<)").matches()) {
                    account.setProperty("nopremium", true);
                } else {
                    account.setProperty("nopremium", false);
                }
                /** Save cookies */
                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies add = this.br.getCookies(COOKIE_HOST);
                for (final Cookie c : add.getCookies()) {
                    cookies.put(c.getKey(), c.getValue());
                }
                account.setProperty("name", Encoding.urlEncode(account.getUser()));
                account.setProperty("pass", Encoding.urlEncode(account.getPass()));
                account.setProperty("cookies", cookies);
            } catch (final PluginException e) {
                account.setProperty("cookies", Property.NULL);
                throw e;
            }
        }
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        /* workaround for free/premium issue on stable 09581 */
        return maxPrem.get();
    }

    /** This removes fake messages which can kill the plugin */
    public void correctBR() throws NumberFormatException, PluginException {
        correctedBR = br.toString();
        ArrayList<String> someStuff = new ArrayList<String>();
        ArrayList<String> regexStuff = new ArrayList<String>();
        regexStuff.add("<\\!(\\-\\-.*?\\-\\-)>");
        regexStuff.add("(display: none;\">.*?</div>)");
        regexStuff.add("(visibility:hidden>.*?<)");
        for (String aRegex : regexStuff) {
            String lolz[] = br.getRegex(aRegex).getColumn(0);
            if (lolz != null) {
                for (String dingdang : lolz) {
                    someStuff.add(dingdang);
                }
            }
        }
        for (String fun : someStuff) {
            correctedBR = correctedBR.replace(fun, "");
        }
    }

    public String getDllink() {
        String dllink = br.getRedirectLocation();
        if (dllink == null) {
            dllink = new Regex(correctedBR, "(\"|\\')(https?://(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|([\\w\\-]+\\.)?" + DOMAINS + ")(:\\d{1,4})?/(files|d|cgi\\-bin/dl\\.cgi)/(\\d+/)?[a-z0-9]+/[^<>\"/]*?)(\"|\\')").getMatch(1);
            if (dllink == null) {
                final String cryptedScripts[] = new Regex(correctedBR, "p\\}\\((.*?)\\.split\\('\\|'\\)").getColumn(0);
                if (cryptedScripts != null && cryptedScripts.length != 0) {
                    for (String crypted : cryptedScripts) {
                        dllink = decodeDownloadLink(crypted);
                        if (dllink != null) break;
                    }
                }
            }
        }
        return dllink;
    }

    public void checkErrors(DownloadLink theLink, boolean checkAll, String passCode) throws NumberFormatException, PluginException {
        if (checkAll) {
            if (new Regex(correctedBR, PASSWORDTEXT).matches() || correctedBR.contains("Wrong password")) {
                logger.warning("Wrong password, the entered password \"" + passCode + "\" is wrong, retrying...");
                throw new PluginException(LinkStatus.ERROR_RETRY, "Wrong password entered");
            }
            if (correctedBR.contains("Wrong captcha") || correctedBR.contains("keycaptcha.com/")) {
                logger.warning("Wrong captcha or wrong password!");
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
            if (correctedBR.contains("\">Skipped countdown<")) throw new PluginException(LinkStatus.ERROR_FATAL, "Fatal countdown error (countdown skipped)");
        }
        /** Wait time reconnect handling */
        if (new Regex(correctedBR, "(You have reached the download\\-limit|You have to wait)").matches()) {
            String tmphrs = new Regex(correctedBR, "\\s+(\\d+)\\s+hours?").getMatch(0);
            if (tmphrs == null) tmphrs = new Regex(correctedBR, "You have to wait.*?\\s+(\\d+)\\s+hours?").getMatch(0);
            String tmpmin = new Regex(correctedBR, "\\s+(\\d+)\\s+minutes?").getMatch(0);
            if (tmpmin == null) tmpmin = new Regex(correctedBR, "You have to wait.*?\\s+(\\d+)\\s+minutes?").getMatch(0);
            String tmpsec = new Regex(correctedBR, "\\s+(\\d+)\\s+seconds?").getMatch(0);
            String tmpdays = new Regex(correctedBR, "\\s+(\\d+)\\s+days?").getMatch(0);
            if (tmphrs == null && tmpmin == null && tmpsec == null && tmpdays == null) {
                logger.info("Waittime regexes seem to be broken");
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, null, 60 * 60 * 1000l);
            } else {
                int minutes = 0, seconds = 0, hours = 0, days = 0;
                if (tmphrs != null) hours = Integer.parseInt(tmphrs);
                if (tmpmin != null) minutes = Integer.parseInt(tmpmin);
                if (tmpsec != null) seconds = Integer.parseInt(tmpsec);
                if (tmpdays != null) days = Integer.parseInt(tmpdays);
                int waittime = ((days * 24 * 3600) + (3600 * hours) + (60 * minutes) + seconds + 1) * 1000;
                logger.info("Detected waittime #2, waiting " + waittime + "milliseconds");
                /** Not enough wait time to reconnect->Wait and try again */
                if (waittime < 180000) { throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, JDL.L("plugins.hoster.xfilesharingprobasic.allwait", ALLWAIT_SHORT), waittime); }
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, null, waittime);
            }
        }
        if (correctedBR.contains("You're using all download slots for IP")) { throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, null, 10 * 60 * 1001l); }
        if (correctedBR.contains("Error happened when generating Download Link")) throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error!", 10 * 60 * 1000l);
        /** Error handling for only-premium links */
        if (new Regex(correctedBR, "( can download files up to |Upgrade your account to download bigger files|>Upgrade your account to download larger files|>The file You requested  reached max downloads limit for Free Users|Please Buy Premium To download this file<|This file reached max downloads limit)").matches()) {
            String filesizelimit = new Regex(correctedBR, "You can download files up to(.*?)only").getMatch(0);
            if (filesizelimit != null) {
                filesizelimit = filesizelimit.trim();
                logger.warning("As free user you can download files up to " + filesizelimit + " only");
                throw new PluginException(LinkStatus.ERROR_FATAL, "Free users can only download files up to " + filesizelimit);
            } else {
                logger.warning("Only downloadable via premium");
                throw new PluginException(LinkStatus.ERROR_FATAL, "Only downloadable via premium or registered");
            }
        }
        if (correctedBR.contains(MAINTENANCE)) throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, JDL.L("plugins.hoster.xfilesharingprobasic.undermaintenance", MAINTENANCEUSERTEXT), 2 * 60 * 60 * 1000l);
    }

    public void checkServerErrors() throws NumberFormatException, PluginException {
        if (new Regex(correctedBR, Pattern.compile("No file", Pattern.CASE_INSENSITIVE)).matches()) throw new PluginException(LinkStatus.ERROR_FATAL, "Server error");
        if (new Regex(correctedBR, "(File Not Found|<h1>404 Not Found</h1>)").matches()) {
            logger.warning("Server says link offline, please recheck that!");
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
    }

    private String decodeDownloadLink(String s) {
        String decoded = null;

        try {
            Regex params = new Regex(s, "\\'(.*?[^\\\\])\\',(\\d+),(\\d+),\\'(.*?)\\'");

            String p = params.getMatch(0).replaceAll("\\\\", "");
            int a = Integer.parseInt(params.getMatch(1));
            int c = Integer.parseInt(params.getMatch(2));
            String[] k = params.getMatch(3).split("\\|");

            while (c != 0) {
                c--;
                if (k[c].length() != 0) p = p.replaceAll("\\b" + Integer.toString(c, a) + "\\b", k[c]);
            }

            decoded = p;
        } catch (Exception e) {
        }

        String finallink = null;
        if (decoded != null) {
            finallink = new Regex(decoded, "name=\"src\"value=\"(.*?)\"").getMatch(0);
            if (finallink == null) {
                finallink = new Regex(decoded, "type=\"video/divx\"src=\"(.*?)\"").getMatch(0);
                if (finallink == null) {
                    finallink = new Regex(decoded, "\\.addVariable\\(\\'file\\',\\'(http://.*?)\\'\\)").getMatch(0);
                }
            }
        }
        return finallink;
    }

    public String handlePassword(String passCode, Form pwform, DownloadLink thelink) throws IOException, PluginException {
        passCode = thelink.getStringProperty("pass", null);
        if (passCode == null) passCode = Plugin.getUserInput("Password?", thelink);
        pwform.put("password", passCode);
        logger.info("Put password \"" + passCode + "\" entered by user in the DLForm.");
        return Encoding.urlEncode(passCode);
    }

    public void prepBrowser(final Browser br) {
        // define custom browser headers and language settings.
        br.getHeaders().put("Accept-Language", "en-US,en;q=0.5");
        br.getHeaders().put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:21.0) Gecko/20100101 Firefox/21.0");
        br.setCookie(COOKIE_HOST, "lang", "english");
        br.setCookie(COOKIE_HOST, "rgoods_1", "1");
        br.setCookie(COOKIE_HOST, "ad_rot", "0");
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    private void waitTime(long timeBefore, DownloadLink downloadLink) throws PluginException {
        int passedTime = (int) ((System.currentTimeMillis() - timeBefore) / 1000) - 1;
        /** Ticket Time */
        String ttt = new Regex(correctedBR, "id=\"countdown_str\">[^<>\"]+<span id=\"[^<>\"]+\"( class=\"[^<>\"]+\")?>([\n ]+)?(\\d+)([\n ]+)?</span>").getMatch(2);
        if (ttt == null) ttt = new Regex(correctedBR, "<h2>Wait <span id=\"[a-z0-9]+\">(\\d+)</span> seconds</h2>").getMatch(0);
        if (ttt != null) {
            int tt = Integer.parseInt(ttt);
            tt -= passedTime;
            logger.info("Waittime detected, waiting " + ttt + " - " + passedTime + " seconds from now on...");
            if (tt > 0) sleep(tt * 1000l, downloadLink);
        }
    }

}