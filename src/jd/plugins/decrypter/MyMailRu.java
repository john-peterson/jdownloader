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
import java.util.Random;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "my.mail.ru" }, urls = { "http://(www\\.)?my\\.mail\\.ru/[^<>/\"]+/[^<>/\"]+/photo\\?album_id=[a-z0-9\\-_]+" }, flags = { 0 })
public class MyMailRu extends PluginForDecrypt {

    public MyMailRu(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.getPage(parameter);
        if (br.containsHTML("class=oranzhe><b>Ошибка</b>")) {
            logger.info("Link offline: " + parameter);
            return decryptedLinks;
        }
        String fpName = br.getRegex("<h1 class=\"l\\-header1\">([^<>\"]*?)</h1>").getMatch(0);
        int offset = 0;
        final String imgC = br.getRegex("\"imagesTotal\": \"(\\d+)\"").getMatch(0);
        if (imgC == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        final Regex parameterStuff = new Regex(parameter, "http://(www\\.)?my\\.mail\\.ru/[^<>/\"]+/([^<>/\"]+)/photo\\?album_id=(.+)");
        final String albumID = parameterStuff.getMatch(2);
        final String username = parameterStuff.getMatch(1);
        final double maxPicsPerSegment = 50;

        final int imgCount = Integer.parseInt(imgC);
        final int pageCount = (int) StrictMath.ceil(imgCount / maxPicsPerSegment);
        int segment = 1;
        while (decryptedLinks.size() != imgCount) {
            logger.info("Decrypting segment " + segment + " of maybe " + pageCount + " segments...");
            String[] links = null;
            if (offset == 0) {
                links = br.getRegex("style=\"background\\-image:url\\((http://[^<>\"]*?/p\\-\\d+\\.jpg)\\)").getColumn(0);
            } else {
                br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                br.getPage("http://my.mail.ru/bk/" + username + "/ajax?ajax_call=1&func_name=photo.photostream&mna=false&mnb=false&encoding=windows-1251&arg_offset=" + offset + "&arg_marker=" + new Random().nextInt(1000) + "&arg_album_id=" + albumID);
                links = br.getRegex("background\\-image:url\\((http://[^<>\"]*?/p\\-\\d+\\.jpg)\\)").getColumn(0);
            }
            if (links == null || links.length == 0) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            for (String singleLink : links) {

                final String ending = singleLink.substring(singleLink.lastIndexOf("/"));
                final DownloadLink dl = createDownloadlink("directhttp://" + singleLink.replace(ending, ending.replace("/p", "/i")));
                dl.setAvailable(true);
                decryptedLinks.add(dl);
            }
            offset += maxPicsPerSegment;
            segment++;
        }
        if (fpName != null) {
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }
}