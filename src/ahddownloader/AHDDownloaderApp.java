/*
 * AHDDownloaderApp.java
 */

package ahddownloader;

import chrriis.dj.nativeswing.swtimpl.NativeInterface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import org.jdesktop.application.Application;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import pt.unl.fct.di.tsantos.util.FileUtils;
import pt.unl.fct.di.tsantos.util.net.WebRequest;
import pt.unl.fct.di.tsantos.util.app.DefaultSingleFrameApplication;

/**
 * The main class of the application.
 */
public class AHDDownloaderApp extends DefaultSingleFrameApplication {

    protected AHDDownloaderView view;
    protected String ahdSpecsFileLocation;

    @Override
    protected void initApplication() {
        super.initApplication();
        ahdSpecsFileLocation = getContext().getResourceMap()
                .getString("Application.ahdSpecsFileLocation");
    }

    /**
     * At startup create and show the main frame of the application.
     */
    @Override protected void startup() {
        NativeInterface.open();
        NativeInterface.runEventPump();
        super.startup();
        show(view = new AHDDownloaderView(this));
    }

    protected void updateShowList() throws IOException, SAXException {
        populateSettingsDirectory();
        File xmlFile = new File(getSettingsDirectory(), "ahdspecs.xml");
        double old_version = -1.0;
        InputStream is = null;
        if (xmlFile.exists()) {
            Document doc = WebRequest.getDocumentD(xmlFile.toURI().toURL());
            Element elem = doc.getDocumentElement();
            NodeList elementsByTagName = elem.getElementsByTagName("version");
            if (elementsByTagName == null || elementsByTagName.getLength() <= 0)
                return;
            Element versionElem = (Element) elementsByTagName.item(0);
            if (versionElem == null) return;
            Node firstChild = versionElem.getFirstChild();
            if (firstChild == null) return;
            String text = firstChild.getTextContent();
            if (text == null) return;
            old_version = Double.parseDouble(text);
        }
        URL url = new URL(ahdSpecsFileLocation);
        Document doc = WebRequest.getDocumentD(url);
        Element elem = doc.getDocumentElement();
        NodeList elementsByTagName = elem.getElementsByTagName("version");
        if (elementsByTagName == null || elementsByTagName.getLength() <= 0)
            return;
        Element versionElem = (Element) elementsByTagName.item(0);
        if (versionElem == null) return;
        Node firstChild = versionElem.getFirstChild();
        if (firstChild == null) return;
        String text = firstChild.getTextContent();
        if (text == null) return;
        double new_version = Double.parseDouble(text);
        if (new_version > old_version) {
            URLConnection conn = url.openConnection();
            is = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(xmlFile);
            int i = -1;
            while ((i = is.read()) != -1) {
                fos.write(i);
            }
            fos.flush();
            fos.close();
            is.close();
            if (view != null) view.refreshAHDSpecs();
        }
        lastUpdate = Calendar.getInstance().getTime();
        settings.put("lastUpdate", lastUpdate.getTime() + "");
    }

    /**
     * A convenient static getter for the application instance.
     * @return the instance of AHDDownloaderApp
     */
    public static AHDDownloaderApp getApplication() {
        return Application.getInstance(AHDDownloaderApp.class);
    }

    /**
     * Main method launching the application.
     */
    public static void main(String[] args) {
        launch(AHDDownloaderApp.class, args);
    }

    protected void createSettingsDirectory() {}

    @Override
    protected void populateSettingsDirectory() {
        File xmlFile = new File(getSettingsDirectory(), "ahdspecs.xml");
        if (!xmlFile.exists()) {
            try {
                InputStream is = AHDDownloaderApp.class.getResourceAsStream(
                        "resources/ahdspecs.xml");
                FileOutputStream fos = new FileOutputStream(xmlFile);
                FileUtils.copy(is, fos);
                is.close();
                fos.close();
            } catch (IOException ex) {
                // Should not happen
            }
        }
    }

    @Override
    protected void update() throws Exception {
        updateShowList();
    }

    @Override
    protected String initSettingsDirectory() {
        return ".ahddownloader";
    }

    @Override
    protected URL initWebLocation() {
        try {
            String webLocation = getContext().getResourceMap()
                    .getString("Application.webLocation");
            return new URL(webLocation);
        } catch (MalformedURLException ex) {
            return null;
        }
    }

    @Override
    protected String initName() {
        return "AHD Downloader";
    }

    @Override
    protected Long initUpdateInterval() {
        return new Long(12*60*60*1000);
    }
}
