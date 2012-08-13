//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
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

package jd.plugins.decrypter;

import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;

//Decrypts embedded videos from vidaru.com
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "vidaru.com" }, urls = { "http://(www\\.)?vidaru\\.com/[a-z0-9\\-_]+/\\d+" }, flags = { 0 })
public class VidaruCom extends PluginForDecrypt {

    public VidaruCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.getPage(parameter);
        String filename = br.getRegex("<span class=\\'vbaslik\\'>([^<>\"]*?)</span>").getMatch(0);
        if (filename == null) filename = br.getRegex("<title>([^<>\"]*?) \\- vidaru\\.com</title>").getMatch(0);
        String externID = br.getRegex("(\"|\\')(http://player\\.vimeo\\.com/video/\\d+)").getMatch(1);
        if (externID != null) {
            DownloadLink dl = createDownloadlink(externID);
            dl.setProperty("Referer", parameter);
            decryptedLinks.add(dl);
        }
        // filename needed for all IDs below here
        if (filename == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        filename = Encoding.htmlDecode(filename.trim());
        externID = br.getRegex("name=\\'movie\\' value=\\'(http://tr\\.netlog\\.com/go/widget/videoID=tr\\-\\d+)\\'").getMatch(0);
        if (externID != null) {
            br.setFollowRedirects(false);
            br.getPage(externID);
            externID = br.getRedirectLocation();
            if (externID == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            externID = new Regex(externID, "(videoID=[^<>\"]*?)\\&baseUrl=").getMatch(0);
            if (externID == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            br.getPage("http://tr.netlog.com/go/ajax/videos/action=getVideoDetails&" + externID);
            externID = br.getRegex("sourceUrl\":\"(http:[^<>\"]*?)\"").getMatch(0);
            if (externID == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            final DownloadLink dl = createDownloadlink("directhttp://" + externID.replace("\\", ""));
            dl.setFinalFileName(Encoding.htmlDecode(filename.trim()) + ".flv");
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        logger.warning("Decrypter broken for link: " + parameter);
        return null;
    }

}