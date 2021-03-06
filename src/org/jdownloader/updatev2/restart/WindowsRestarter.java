package org.jdownloader.updatev2.restart;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import org.appwork.utils.Application;
import org.appwork.utils.os.CrossSystem;
import org.jdownloader.updatev2.RestartController;

public class WindowsRestarter extends Restarter {

    @Override
    protected List<String> getApplicationStartCommands(File root) {
        ArrayList<String> lst = new ArrayList<String>();
        if (new File(root, "JDownloader.exe").exists()) {
            lst.add(new File(root, "JDownloader.exe").getAbsolutePath());
            return lst;
        } else if (new File(root, "JDownloader2.exe").exists()) {
            lst.add(new File(root, "JDownloader2.exe").getAbsolutePath());
            return lst;
        } else if (new File(root, "JDownloader 2.exe").exists()) {
            lst.add(new File(root, "JDownloader 2.exe").getAbsolutePath());
            return lst;
        } else {
            lst.addAll(getJVMApplicationStartCommands(root));
            return lst;
        }
    }

    @Override
    protected List<String> getJVMApplicationStartCommands(File root) {

        final java.util.List<String> jvmParameter = new ArrayList<String>();

        jvmParameter.add(CrossSystem.getJavaBinary());

        final List<String> lst = ManagementFactory.getRuntimeMXBean().getInputArguments();

        boolean xmsset = false;
        boolean useconc = false;
        boolean minheap = false;
        boolean maxheap = false;

        for (final String h : lst) {
            if (h.contains("Xmx")) {
                if (Runtime.getRuntime().maxMemory() < 533000000) {
                    jvmParameter.add("-Xmx512m");
                    continue;
                }
            } else if (h.contains("xms")) {
                xmsset = true;
                jvmParameter.add(h);
            } else if (h.contains("XX:+useconc")) {
                useconc = true;
                jvmParameter.add(h);
            } else if (h.contains("minheapfree")) {
                minheap = true;
                jvmParameter.add(h);
            } else if (h.contains("maxheapfree")) {
                maxheap = true;
                jvmParameter.add(h);
            } else if (h.startsWith("-agentlib:")) {
                continue;
            }

        }
        if (!xmsset) {

            jvmParameter.add("-Xms64m");
        }
        jvmParameter.add("-jar");
        jvmParameter.add(Application.getJarName(RestartController.class));
        return jvmParameter;

    }

}
