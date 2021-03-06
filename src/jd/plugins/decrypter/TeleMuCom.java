//    jDownloader - Downloadmanager
//    Copyright (C) 2011  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "telechargementmu.com" }, urls = { "http://(www\\.)?telechargementmu\\.com/.*\\.html|http://feedproxy\\.google\\.com/~r/telechargementmu/.*\\.html" }, flags = { 0 })
public class TeleMuCom extends PluginForDecrypt {

    public TeleMuCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        if (parameter.startsWith("http://feedproxy.google.com")) {
            br.getPage(parameter);
            parameter = br.getRedirectLocation();
        }
        br.setFollowRedirects(false);
        // The site needs authentication to works (It seems that the browser
        // cookie does not work, then I add it here)
        br.setCookie(parameter, "dle_user_id", "9756");
        br.setCookie(parameter, "dle_password", "23d45b337ff85d0a326a79082f7c6f50");
        br.setCookie(parameter, "dle_hash", "7c129fda950295919ce7e98df4b29d4e");

        br.getPage(parameter);
        String fpName = br.getRegex("<title>(.*?)</title>").getMatch(0);
        if (fpName == null) {
            fpName = br.getRegex("<h3>(.*?)</h3>").getMatch(0);
        }

        // On this site, when an accent is set, the result is as extended ASCII
        // character which is bad in the name of the package
        fpName = Encoding.htmlDecode(fpName);
        fpName = RemoveCharacter(fpName);

        int iLinkImage = 0;
        String[] TabImage = br.getRegex("<img[^>]+src\\s*=\\s*[\\'\"](http://[^\\'\"]+)\\.jpg[\\'\"][^>]*>").getColumn(0);

        if (TabImage != null) {
            for (String strImageLink : TabImage) {
                TabImage[iLinkImage] = strImageLink + ".jpg";
                iLinkImage++;
            }
        }

        String[] TabPassword = br.getRegex("</a>[ \t]+([A-Z0-9][^ ]*)[ \t]+<a href").getColumn(0);

        // Creation of the array of link that is supported by all plug-in
        final String[] links = br.getRegex("href=\"(.*?)\"").getColumn(0);
        if (links == null || links.length == 0) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }

        String[] linksCrypted = br.getRegex("\"(http://telechargementmu\\.com/engine/go\\.php\\?url=.*?)\"").getColumn(0);

        // Added links
        for (String directlink : links) {
            decryptedLinks.add(createDownloadlink(directlink));
        }

        // Added crypted links
        for (String redirectlink : linksCrypted) {
            br.getPage(redirectlink);
            String finallink = br.getRedirectLocation();
            if (finallink != null) {
                if (finallink.toLowerCase().contains("revivelink")) {
                    finallink = finallink + "###" + fpName.trim();
                }
                decryptedLinks.add(createDownloadlink(finallink));
            }
        }

        if (TabImage != null) {
            for (String strImageLink : TabImage) {
                decryptedLinks.add(createDownloadlink(strImageLink, false));
            }
        }

        for (int i = decryptedLinks.size() - 1; i >= 0; i--) {
            if (decryptedLinks.get(i) == null) {
                decryptedLinks.remove(i);
            }
        }

        if (fpName != null) {
            FilePackage fp = FilePackage.getInstance();
            fp.setName(fpName.trim());
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }

    @Override
    protected DownloadLink createDownloadlink(String link) {
        return createDownloadlink(link, true);
    }

    protected DownloadLink createDownloadlink(String link, Boolean bVerify) {
        if (!link.startsWith("http")) return null;
        link = link.replace("http://www.", "http://");
        if (bVerify && link.startsWith("http://telechargementmu")) return null;

        return super.createDownloadlink(link);
    }

    /**
     * Allows to remove some character to have a nice name
     * 
     * @param strName
     *            The name of the package
     * @return the name of the package normalized.
     */
    private String RemoveCharacter(String strName) {
        String strRemover = "( - )|( · )";
        String[] strTemp = strName.split(strRemover);
        if (strTemp.length >= 2) {
            strName = strTemp[0].trim() + " - " + strTemp[1].trim();
        }

        strName = strName.replace("", "-");
        strName = strName.replace("", "'");
        strName = strName.replace(":", ",");

        strName = strName.replace("VA - ", "");

        Pattern pattern = null;
        Matcher matcher = null;
        // Remove the exact words mp3, flac, lossless and ape
        pattern = Pattern.compile("(?<!\\p{L})mp3(?!\\p{L})|(?<!\\p{L})ape(?!\\p{L})|(?<!\\p{L})flac(?!\\p{L})|(?<!\\p{L})lossless(?!\\p{L})", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(strName);
        strName = matcher.replaceAll("");

        // Replace the [ ] with nothing because the string in this bracket has been removed
        pattern = Pattern.compile("\\[ *\\]", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(strName);
        strName = matcher.replaceAll("");

        // Replace the ( ) with nothing because the string in this parenthesis has been removed
        pattern = Pattern.compile("^\\[.*\\] ", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(strName);
        strName = matcher.replaceAll("");

        // Replace the ( ) with nothing because the string in this parenthesis has been removed
        pattern = Pattern.compile("\\( *\\)", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(strName);
        strName = matcher.replaceAll("");

        // Replace several space characters in one
        pattern = Pattern.compile("\\s{2,}", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(strName);
        strName = matcher.replaceAll(" ");

        return strName;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}