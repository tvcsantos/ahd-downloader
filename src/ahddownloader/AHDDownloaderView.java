/*
 * AHDDownloaderView.java
 */
package ahddownloader;

import chrriis.dj.nativeswing.swtimpl.components.JWebBrowser;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Timer;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JComboBox;
import org.jdesktop.application.Action;
import org.jdesktop.application.SingleFrameApplication;
import org.jdesktop.application.FrameView;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import pt.unl.fct.di.tsantos.util.Pair;
import pt.unl.fct.di.tsantos.util.net.RSSFeed;
import pt.unl.fct.di.tsantos.util.time.Ticker;
import pt.unl.fct.di.tsantos.util.awt.TrayIconStreamed;
import pt.unl.fct.di.tsantos.util.download.Downloader;
import pt.unl.fct.di.tsantos.util.download.torrent.TorrentDownloader;
import pt.unl.fct.di.tsantos.util.imdb.IMDB;

/**
 * The application's main frame.
 */
public class AHDDownloaderView extends FrameView {

    static class WantedMovie implements Serializable {

        String title;
        String year;
        String imdbLink;
        String group;
        boolean onlyInternal;
        boolean onlyNonInternal;
        String media;
        String resolution;
        String audformat;
        String encodeStatus;
        boolean onlyFreeleech;
        boolean onlyNonFreeleech;
        String freeleech;
        String imgLink;

        String mediaIcon;
        String resolutionIcon;
        String groupIcon;

        @Override
        public String toString() {
            return title + " (" + year + ")";
        }
    }

    static class AHDDownloaderTask implements Runnable {

        AHDRSSTorrentDownloader down;
        AHDDownloaderView callback;

        public AHDDownloaderTask(AHDRSSTorrentDownloader down,
                AHDDownloaderView callback) {
            this.down = down;
            this.callback = callback;
        }

        @Override
        public void run() {
            try {
                List<Pair<File, WantedMovie>> l = down.downloadEx();
                for (Pair<File, WantedMovie> p : l) {
                    WantedMovie wm = p.getSnd();
                    System.out.println("FOUND " + wm);
                    callback.removeTableEntry(wm);
                }
            } catch (IOException ex) {
            }
        }
    }

    static class AHDRSSTorrentDownloader implements Downloader {

        protected List<WantedMovie> movieList;
        protected List<String> internals;
        protected RSSFeed feed;
        protected File saveDirectory;

        class BestConfig {

            String title;
            String year;
            String group;
            String media;
            String resolution;
            String audformat;
            String encodeStatus;
            String freeleech;
            String torrentLink;
        }

        public AHDRSSTorrentDownloader(List<WantedMovie> list,
                RSSFeed feed,
                List<String> internals,
                File saveDirectory) {
            movieList = list;
            this.feed = feed;
            this.saveDirectory = saveDirectory;
        }

        public List<Pair<File, WantedMovie>> downloadEx() throws IOException {
            LinkedList<Pair<File, WantedMovie>> res =
                    new LinkedList<Pair<File, WantedMovie>>();
            for (WantedMovie m : movieList) {
                try {
                    SyndFeed parseFeed = feed.parseFeed();
                    List<SyndEntry> entries = parseFeed.getEntries();
                    BestConfig bc = null;
                    for (SyndEntry entry : entries) {
                        String title = entry.getTitle();
                        Matcher ma = Pattern.compile(
                                "MOVIE:([^\\[\\]]+)\\[([^/]+)\\] -([^/]+)/"
                                + "([^/]+)/([^/]+)/([^/]+)/([^/]+)(?:/([^/]+))?"
                                + "(?:/([^/]+))?(?:/([^/]+))?",
                                Pattern.CASE_INSENSITIVE).matcher(title);
                        //System.out.println(ma.groupCount());
                        if (ma.matches()) {
                            String etitle = ma.group(1);
                            etitle = etitle != null
                                    ? etitle.trim() : etitle;
                            String eyear = ma.group(2);
                            eyear = eyear != null ? eyear.trim() : eyear;
                            String group = ma.group(3);
                            group = group != null ? group.trim() : group;
                            String media = ma.group(4);
                            media = media != null ? media.trim() : media;
                            String resolution = ma.group(5);
                            resolution = resolution != null
                                    ? resolution.trim() : resolution;
                            String ftype = ma.group(6);
                            ftype = ftype != null ? ftype.trim() : ftype;
                            String audiof = ma.group(7);
                            audiof = audiof != null ? audiof.trim() : audiof;
                            String encstat1 = ma.group(8);
                            encstat1 = encstat1 != null
                                    ? encstat1.trim() : encstat1;
                            String encstat2 = ma.group(9);
                            encstat2 = encstat2 != null
                                    ? encstat2.trim() : encstat2;
                            String fl = ma.group(10);
                            fl = fl != null ? fl.trim() : fl;
                            int xi = fl != null ? fl.indexOf("%") : -1;
                            if (xi != -1) {
                                fl = fl.substring(0, xi);
                            }
                            /*System.out.println(etitle + " "
                            + getIMDBID(etitle));
                            System.out.println(eyear);
                            System.out.println(group);
                            System.out.println(media);
                            System.out.println(resolution);
                            System.out.println(ftype);
                            System.out.println(audiof);*/
                            String encodestat = null;
                            if (encstat1 != null && encstat2 != null) {
                                encodestat = encstat1 + " + " + encstat2;
                            } else if (encstat1 == null && encstat2 != null) {
                                encodestat = encstat2;
                            } else if (encstat1 != null && encstat2 == null) {
                                encodestat = encstat1;
                            }
                            /*System.out.println(encstat1);
                            System.out.println(encstat2);
                            System.out.println(encodestat);
                            System.out.println(fl);*/

                            if (etitle == null) {
                                continue;
                            }

                            if (m.onlyFreeleech && fl == null) {
                                continue;
                            }
                            if (m.onlyNonFreeleech && fl != null) {
                                continue;
                            }
                            if (m.onlyInternal && !internals.contains(group)) {
                                continue;
                            }
                            if (m.onlyNonInternal && internals.contains(group)) {
                                continue;
                            }
                            if (m.title.compareTo(etitle) != 0) {
                                continue;
                            }
                            boolean audioOK = m.audformat.equals("Any")
                                    || (audiof != null
                                    && m.audformat.equals(audiof));

                            boolean freeLOK = (m.onlyFreeleech
                                    && (m.freeleech.equals("Any")
                                    || (fl != null && m.freeleech.equals(fl))))
                                    || (m.onlyNonFreeleech && fl == null)
                                    || (!m.onlyFreeleech && !m.onlyNonFreeleech);

                            boolean grpOK = (m.onlyInternal
                                    && (m.group.equals("Any")
                                    || (group != null && m.group.equals(group))))
                                    || (m.onlyNonInternal
                                    && !internals.contains(group))
                                    || (!m.onlyInternal && !m.onlyNonInternal);

                            boolean medOK = m.media.equals("Any")
                                    || (media != null && m.media.equals(media));

                            boolean resOK = m.resolution.equals("Any")
                                    || (resolution != null
                                    && m.resolution.equals(resolution));



                            if (audioOK && freeLOK && grpOK && medOK && resOK) {
                                if (bc == null) {
                                    bc = new BestConfig();
                                }
                                bc.title = etitle;
                                bc.year = eyear;
                                bc.group = group;
                                bc.media = media;
                                bc.resolution = resolution;
                                bc.audformat = audiof;
                                bc.encodeStatus = encodestat;
                                bc.torrentLink = entry.getLink();
                            }
                        }
                    }
                    if (bc != null) {
                        List<File> ld = TorrentDownloader.download(
                                new URL(bc.torrentLink),
                                null, saveDirectory);
                        if (ld != null) {
                            for (File f : ld) {
                                res.add(new Pair<File, WantedMovie>(f, m));
                            }
                        }
                    }
                } catch (MalformedURLException ex) {
                } catch (IllegalArgumentException ex) {
                } catch (FeedException ex) {
                }
            }
            return res;
        }

        public List<File> download() throws IOException {
            List<Pair<File, WantedMovie>> res = downloadEx();
            List<File> ret = new LinkedList<File>();
            for (Pair<File, WantedMovie> p : res) {
                ret.add(p.getFst());
            }
            return ret;
        }
    }
    List<String> internals;

    public AHDDownloaderView(SingleFrameApplication app) {
        super(app);
        initComponents();
        initMyComponents();
    }

    private void initMyComponents() {
        timer = new Timer();
        currentTask = null;
        favs = new LinkedList<WantedMovie>();

        jTable1.setBackground(Color.WHITE);
        jTable1.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        //jTable1.setEditingRow(0);

        // disable horizontal lines in table
        jTable1.setShowHorizontalLines(false);
        jTable1.setShowVerticalLines(false);
        //jTable1.setRowHeight(55);
        jTable1.setRowHeight(105);
        //jTable1.setDefaultRenderer(TVShowEntry.class, new TableCellRendererImpl());
        jTable1.getColumn("Movie").setCellRenderer(
                new WantedMovieTableCellRender());

        getFrame().setResizable(false);
        addDialog = new JDialog(getFrame(), "Add Movie");
        addDialog.setModal(true);
        JPanel webBrowserPanel = new JPanel(new BorderLayout());
        final JWebBrowser webBrowser = new JWebBrowser();
        webBrowser.setMenuBarVisible(false);
        webBrowser.setLocationBarVisible(false);
        //webBrowser.setBarsVisible(false);
        //webBrowser.setButtonBarVisible(true);
        webBrowser.navigate("http://www.imdb.com");
        webBrowserPanel.add(webBrowser, BorderLayout.CENTER);
        addDialog.add(webBrowserPanel, BorderLayout.CENTER);
        JButton jb = new JButton("Add");
        jb.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                Matcher m1 = Pattern.compile(
                        "http://www.imdb.com/title/tt\\d*/").matcher(
                        webBrowser.getResourceLocation());
                if (m1.matches()) {
                    addToWantList(webBrowser.getResourceLocation());
                    addDialog.setVisible(false);
                    webBrowser.navigate("http://www.imdb.com");
                } else {
                    JOptionPane.showMessageDialog(addDialog,
                            "Invalid imdb movie link!",
                            AHDDownloaderApp.getApplication().getName(),
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        JButton jbc = new JButton("Cancel");
        jbc.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                addDialog.setVisible(false);
                webBrowser.navigate("http://www.imdb.com");
            }
        });
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        buttonPanel.add(jb);
        buttonPanel.add(jbc);

        addDialog.add(buttonPanel, BorderLayout.SOUTH);

        //jTable1.get

        String s = (String) getSettings().get("saveDir");
        saveDirectory = s == null ? null : new File(s);
        if (saveDirectory != null) {
            saveToTextField.setText(saveDirectory.toString());
        }
        s = (String) getSettings().get("searchAtStartup");
        if (s == null) {
            searchAtStartup = false;
        } else {
            searchAtStartup = Boolean.valueOf(s).booleanValue();
        }
        searchAtStartupCheckBox.setSelected(searchAtStartup);
        s = (String) getSettings().get("searchInterval");
        if (s == null) {
            minutes = 30;
        } else {
            minutes = Integer.valueOf(s).intValue();
        }
        searchMinutesTextField.setText(minutes + "");

        nextCheckLabel.setText("");

        refreshAHDSpecs();

        loadTableData();

        tiker = new Ticker(minutes, searchAtStartup);
        Observer observer = new Observer() {

            public void update(Observable o, Object arg) {
                Ticker t = (Ticker) arg;
                if (t != null) {
                    if (t.isRunning()) {
                        nextCheckLabel.setText("Checking for movies");
                    } else {
                        nextCheckLabel.setText("Next check for new "
                                + "movies in " + t.nextRun() + " minutes");
                    }
                }
            }
        };
        tiker.add(observer);
        configurationChanged();

        timer.schedule(tiker, 0, 60 * 1000);
    }

    private void loadTableData() {
        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
                    new File(AHDDownloaderApp.getApplication().getSettingsDirectory(),
                    "data.db")));
            Vector v = (Vector) ois.readObject();
            DefaultTableModel dtm = (DefaultTableModel) jTable1.getModel();
            for (int i = 0; i < v.size(); i++) {
                Vector row = (Vector) v.get(i);
                dtm.addRow(row);
                favs.add((WantedMovie) row.get(0));
            }
        } catch (Exception e) {
        }
    }
    private Map<String, String> micon = new HashMap<String, String>();

    public void refreshAHDSpecs() {
        internals = new LinkedList<String>();
        try {
            internalComboBox.removeAllItems();
            mediaComboBox.removeAllItems();
            freeleechComboBox.removeAllItems();
            resolutionComboBox.removeAllItems();
            audFormatComboBox.removeAllItems();
            encodeStatComboBox.removeAllItems();

            internalComboBox.addItem("Any");
            mediaComboBox.addItem("Any");
            freeleechComboBox.addItem("Any");
            resolutionComboBox.addItem("Any");
            audFormatComboBox.addItem("Any");
            encodeStatComboBox.addItem("Any");

            File xml_file = new File(
                    AHDDownloaderApp.getApplication().getSettingsDirectory(),
                    "ahdspecs.xml");
            // Process response
            Document response = DocumentBuilderFactory.newInstance().
                    newDocumentBuilder().parse(xml_file);

            XPathFactory factory = XPathFactory.newInstance();
            XPath xPath = factory.newXPath();

            //Get all internalgroups
            NodeList nodes =
                    (NodeList) xPath.evaluate("/ahdspecs/internalgroups/group",
                    response, XPathConstants.NODESET);
            int nodeCount = nodes.getLength();

            int nc = nodes.getLength();
            for (int j = 0; j < nc; j++) {
                Node nn = nodes.item(j);
                String s = nn.getTextContent();
                if (s == null || s.length() <= 0) {
                    continue;
                }
                internals.add(s);
                internalComboBox.addItem(s);
            }
            //addTo(internalComboBox, nodes);

            //Get all ripspecifics
            nodes =
                    (NodeList) xPath.evaluate("/ahdspecs/ripspecifics",
                    response, XPathConstants.NODESET);
            nodeCount = nodes.getLength();

            for (int i = 0; i < nodeCount; i++) {
                Node n = nodes.item(i);

                // Get all media
                NodeList ns = (NodeList) xPath.evaluate("media", n,
                        XPathConstants.NODESET);
                addTo(mediaComboBox, ns);

                // Get all resolution
                ns = (NodeList) xPath.evaluate("resolution", n,
                        XPathConstants.NODESET);
                addTo(resolutionComboBox, ns);

                // Get all audioformat
                ns = (NodeList) xPath.evaluate("audioformat", n,
                        XPathConstants.NODESET);
                addTo(audFormatComboBox, ns);

                // Get all encodestatus
                ns = (NodeList) xPath.evaluate("encodestatus", n,
                        XPathConstants.NODESET);
                addTo(encodeStatComboBox, ns);
            }

            //Get all freeleech
            nodes = (NodeList) xPath.evaluate("/ahdspecs/misc/freeleech",
                    response, XPathConstants.NODESET);
            nodeCount = nodes.getLength();

            addTo(freeleechComboBox, nodes);
        } catch (Exception e) {
        }
    }

    private void addTo(JComboBox jcb, NodeList ns) {
        int nc = ns.getLength();
        for (int j = 0; j < nc; j++) {
            Node nn = ns.item(j);
            String s = nn.getTextContent();
            if (s == null || s.length() <= 0) {
                continue;
            }
            jcb.addItem(s);
            
            Node n2 = nn.getAttributes().getNamedItem("icon");
            if (n2 == null) {
                continue;
            }
            String s2 = n2.getTextContent();
            if (s2 == null || s.length() <= 0) {
                continue;
            }
            micon.put(s, s2);
        }
    }

    private void addToWantList(String url) {
        curr = new WantedMovie();
        curr.imdbLink = url;
        Pair<String, String> p;
        try {
            p = IMDB.getOriginalTitle(url);
            curr.title = p.getFst();
            curr.year = p.getSnd();
            curr.imgLink = IMDB.getPoster(url);
        } catch (IOException ex) {
        }
        jDialog1.pack();
        jDialog1.setLocationRelativeTo(addDialog);
        jDialog1.setVisible(true);
    }
    private WantedMovie curr = null;

    @Action
    public void showAboutBox() {
        if (aboutBox == null) {
            JFrame mainFrame = AHDDownloaderApp.getApplication().getMainFrame();
            aboutBox = new AHDDownloaderAboutBox(mainFrame);
            aboutBox.setLocationRelativeTo(mainFrame);
        }
        AHDDownloaderApp.getApplication().show(aboutBox);
    }

    @Action
    public void showAddDialog() {
        addDialog.setLocationRelativeTo(getFrame());
        addDialog.setSize(800, 600);
        AHDDownloaderApp.getApplication().show(addDialog);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainPanel = new javax.swing.JPanel();
        jToolBar1 = new javax.swing.JToolBar();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jButton4 = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        findLabel = new javax.swing.JLabel();
        saveToLabel = new javax.swing.JLabel();
        saveToTextField = new javax.swing.JTextField();
        saveBrowseButton = new javax.swing.JButton();
        nextCheckLabel = new javax.swing.JLabel();
        jSeparator3 = new javax.swing.JSeparator();
        jSeparator4 = new javax.swing.JSeparator();
        searchEpisodesLabel = new javax.swing.JLabel();
        searchMinutesTextField = new javax.swing.JTextField();
        searchMinutesLabel = new javax.swing.JLabel();
        searchAtStartupCheckBox = new javax.swing.JCheckBox();
        jSeparator5 = new javax.swing.JSeparator();
        menuBar = new javax.swing.JMenuBar();
        javax.swing.JMenu fileMenu = new javax.swing.JMenu();
        javax.swing.JMenuItem exitMenuItem = new javax.swing.JMenuItem();
        javax.swing.JMenu helpMenu = new javax.swing.JMenu();
        javax.swing.JMenuItem aboutMenuItem = new javax.swing.JMenuItem();
        jDialog1 = new javax.swing.JDialog();
        jLabel1 = new javax.swing.JLabel();
        internalComboBox = new javax.swing.JComboBox();
        mediaComboBox = new javax.swing.JComboBox();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        releaseAllRadioButton = new javax.swing.JRadioButton();
        onlyInternalRadioButton = new javax.swing.JRadioButton();
        onlyNonIntRadioButton = new javax.swing.JRadioButton();
        allTypeRadioButton = new javax.swing.JRadioButton();
        onlyNonFreeRadioButton = new javax.swing.JRadioButton();
        onlyFreeRadioButton = new javax.swing.JRadioButton();
        freeleechComboBox = new javax.swing.JComboBox();
        resolutionComboBox = new javax.swing.JComboBox();
        audFormatComboBox = new javax.swing.JComboBox();
        encodeStatComboBox = new javax.swing.JComboBox();
        jSeparator1 = new javax.swing.JSeparator();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        okButton = new javax.swing.JButton();
        jSeparator2 = new javax.swing.JSeparator();
        cancelButton = new javax.swing.JButton();
        buttonGroup1 = new javax.swing.ButtonGroup();
        buttonGroup2 = new javax.swing.ButtonGroup();

        mainPanel.setName("mainPanel"); // NOI18N

        jToolBar1.setFloatable(false);
        jToolBar1.setRollover(true);
        jToolBar1.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        jToolBar1.setName("jToolBar1"); // NOI18N

        org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(ahddownloader.AHDDownloaderApp.class).getContext().getResourceMap(AHDDownloaderView.class);
        jButton1.setIcon(resourceMap.getIcon("jButton1.icon")); // NOI18N
        jButton1.setText(resourceMap.getString("jButton1.text")); // NOI18N
        jButton1.setFocusable(false);
        jButton1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton1.setName("jButton1"); // NOI18N
        jButton1.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });
        jToolBar1.add(jButton1);

        jButton2.setIcon(resourceMap.getIcon("jButton2.icon")); // NOI18N
        jButton2.setText(resourceMap.getString("jButton2.text")); // NOI18N
        jButton2.setFocusable(false);
        jButton2.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton2.setName("jButton2"); // NOI18N
        jButton2.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });
        jToolBar1.add(jButton2);

        jButton3.setIcon(resourceMap.getIcon("jButton3.icon")); // NOI18N
        jButton3.setText(resourceMap.getString("jButton3.text")); // NOI18N
        jButton3.setFocusable(false);
        jButton3.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton3.setName("jButton3"); // NOI18N
        jButton3.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });
        jToolBar1.add(jButton3);

        jButton4.setIcon(resourceMap.getIcon("jButton4.icon")); // NOI18N
        jButton4.setText(resourceMap.getString("jButton4.text")); // NOI18N
        jButton4.setFocusable(false);
        jButton4.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton4.setName("jButton4"); // NOI18N
        jButton4.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jToolBar1.add(jButton4);

        jScrollPane1.setName("jScrollPane1"); // NOI18N

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Movie"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jTable1.setName("jTable1"); // NOI18N
        jTable1.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(jTable1);
        jTable1.getColumnModel().getColumn(0).setResizable(false);
        jTable1.getColumnModel().getColumn(0).setHeaderValue(resourceMap.getString("jTable1.columnModel.title0")); // NOI18N

        findLabel.setText(resourceMap.getString("findLabel.text")); // NOI18N
        findLabel.setName("findLabel"); // NOI18N

        saveToLabel.setText(resourceMap.getString("saveToLabel.text")); // NOI18N
        saveToLabel.setName("saveToLabel"); // NOI18N

        saveToTextField.setEditable(false);
        saveToTextField.setName("saveToTextField"); // NOI18N

        saveBrowseButton.setIcon(resourceMap.getIcon("saveBrowseButton.icon")); // NOI18N
        saveBrowseButton.setText(resourceMap.getString("saveBrowseButton.text")); // NOI18N
        saveBrowseButton.setName("saveBrowseButton"); // NOI18N
        saveBrowseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveBrowseButtonActionPerformed(evt);
            }
        });

        nextCheckLabel.setText(resourceMap.getString("nextCheckLabel.text")); // NOI18N
        nextCheckLabel.setName("nextCheckLabel"); // NOI18N

        jSeparator3.setName("jSeparator3"); // NOI18N

        jSeparator4.setName("jSeparator4"); // NOI18N

        searchEpisodesLabel.setText(resourceMap.getString("searchEpisodesLabel.text")); // NOI18N
        searchEpisodesLabel.setName("searchEpisodesLabel"); // NOI18N

        searchMinutesTextField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        searchMinutesTextField.setName("searchMinutesTextField"); // NOI18N
        searchMinutesTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchMinutesTextFieldActionPerformed(evt);
            }
        });

        searchMinutesLabel.setText(resourceMap.getString("searchMinutesLabel.text")); // NOI18N
        searchMinutesLabel.setName("searchMinutesLabel"); // NOI18N

        searchAtStartupCheckBox.setText(resourceMap.getString("searchAtStartupCheckBox.text")); // NOI18N
        searchAtStartupCheckBox.setName("searchAtStartupCheckBox"); // NOI18N
        searchAtStartupCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchAtStartupCheckBoxActionPerformed(evt);
            }
        });

        jSeparator5.setName("jSeparator5"); // NOI18N

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(jSeparator3, javax.swing.GroupLayout.DEFAULT_SIZE, 405, Short.MAX_VALUE)
                        .addContainerGap())
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(jSeparator4, javax.swing.GroupLayout.DEFAULT_SIZE, 405, Short.MAX_VALUE)
                        .addContainerGap())
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(searchAtStartupCheckBox)
                        .addContainerGap())
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, mainPanelLayout.createSequentialGroup()
                        .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jSeparator5, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 403, Short.MAX_VALUE)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 403, Short.MAX_VALUE))
                        .addGap(12, 12, 12))
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(searchEpisodesLabel)
                        .addContainerGap(275, Short.MAX_VALUE))
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(searchMinutesTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(searchMinutesLabel)
                        .addContainerGap(337, Short.MAX_VALUE))
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(mainPanelLayout.createSequentialGroup()
                                .addComponent(findLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 301, Short.MAX_VALUE))
                            .addGroup(mainPanelLayout.createSequentialGroup()
                                .addComponent(saveToLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 361, Short.MAX_VALUE))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, mainPanelLayout.createSequentialGroup()
                                .addComponent(saveToTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 314, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(saveBrowseButton)
                                .addGap(8, 8, 8))
                            .addComponent(nextCheckLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 262, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addContainerGap())))
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addGap(88, 88, 88)
                .addComponent(jToolBar1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(69, Short.MAX_VALUE))
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addComponent(jToolBar1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 264, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator5, javax.swing.GroupLayout.PREFERRED_SIZE, 9, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(searchEpisodesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(searchMinutesTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(searchMinutesLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(searchAtStartupCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator3, javax.swing.GroupLayout.DEFAULT_SIZE, 1, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(findLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(saveToLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(saveToTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(saveBrowseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator4, javax.swing.GroupLayout.PREFERRED_SIZE, 5, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(nextCheckLabel)
                .addGap(12, 12, 12))
        );

        menuBar.setName("menuBar"); // NOI18N

        fileMenu.setText(resourceMap.getString("fileMenu.text")); // NOI18N
        fileMenu.setName("fileMenu"); // NOI18N

        javax.swing.ActionMap actionMap = org.jdesktop.application.Application.getInstance(ahddownloader.AHDDownloaderApp.class).getContext().getActionMap(AHDDownloaderView.class, this);
        exitMenuItem.setAction(actionMap.get("quit")); // NOI18N
        exitMenuItem.setName("exitMenuItem"); // NOI18N
        fileMenu.add(exitMenuItem);

        menuBar.add(fileMenu);

        helpMenu.setText(resourceMap.getString("helpMenu.text")); // NOI18N
        helpMenu.setName("helpMenu"); // NOI18N

        aboutMenuItem.setAction(actionMap.get("showAboutBox")); // NOI18N
        aboutMenuItem.setName("aboutMenuItem"); // NOI18N
        helpMenu.add(aboutMenuItem);

        menuBar.add(helpMenu);

        jDialog1.setModal(true);
        jDialog1.setName("jDialog1"); // NOI18N

        jLabel1.setText(resourceMap.getString("jLabel1.text")); // NOI18N
        jLabel1.setName("jLabel1"); // NOI18N

        internalComboBox.setName("internalComboBox"); // NOI18N

        mediaComboBox.setName("mediaComboBox"); // NOI18N

        jLabel2.setText(resourceMap.getString("jLabel2.text")); // NOI18N
        jLabel2.setName("jLabel2"); // NOI18N

        jLabel3.setText(resourceMap.getString("jLabel3.text")); // NOI18N
        jLabel3.setName("jLabel3"); // NOI18N

        jLabel4.setText(resourceMap.getString("jLabel4.text")); // NOI18N
        jLabel4.setName("jLabel4"); // NOI18N

        jLabel5.setText(resourceMap.getString("jLabel5.text")); // NOI18N
        jLabel5.setName("jLabel5"); // NOI18N

        buttonGroup1.add(releaseAllRadioButton);
        releaseAllRadioButton.setSelected(true);
        releaseAllRadioButton.setText(resourceMap.getString("releaseAllRadioButton.text")); // NOI18N
        releaseAllRadioButton.setName("releaseAllRadioButton"); // NOI18N

        buttonGroup1.add(onlyInternalRadioButton);
        onlyInternalRadioButton.setText(resourceMap.getString("onlyInternalRadioButton.text")); // NOI18N
        onlyInternalRadioButton.setName("onlyInternalRadioButton"); // NOI18N

        buttonGroup1.add(onlyNonIntRadioButton);
        onlyNonIntRadioButton.setText(resourceMap.getString("onlyNonIntRadioButton.text")); // NOI18N
        onlyNonIntRadioButton.setName("onlyNonIntRadioButton"); // NOI18N

        buttonGroup2.add(allTypeRadioButton);
        allTypeRadioButton.setSelected(true);
        allTypeRadioButton.setText(resourceMap.getString("allTypeRadioButton.text")); // NOI18N
        allTypeRadioButton.setName("allTypeRadioButton"); // NOI18N

        buttonGroup2.add(onlyNonFreeRadioButton);
        onlyNonFreeRadioButton.setText(resourceMap.getString("onlyNonFreeRadioButton.text")); // NOI18N
        onlyNonFreeRadioButton.setName("onlyNonFreeRadioButton"); // NOI18N

        buttonGroup2.add(onlyFreeRadioButton);
        onlyFreeRadioButton.setText(resourceMap.getString("onlyFreeRadioButton.text")); // NOI18N
        onlyFreeRadioButton.setName("onlyFreeRadioButton"); // NOI18N

        freeleechComboBox.setName("freeleechComboBox"); // NOI18N

        resolutionComboBox.setName("resolutionComboBox"); // NOI18N

        audFormatComboBox.setName("audFormatComboBox"); // NOI18N

        encodeStatComboBox.setName("encodeStatComboBox"); // NOI18N

        jSeparator1.setName("jSeparator1"); // NOI18N

        jLabel6.setText(resourceMap.getString("jLabel6.text")); // NOI18N
        jLabel6.setName("jLabel6"); // NOI18N

        jLabel7.setText(resourceMap.getString("jLabel7.text")); // NOI18N
        jLabel7.setName("jLabel7"); // NOI18N

        okButton.setText(resourceMap.getString("okButton.text")); // NOI18N
        okButton.setName("okButton"); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        jSeparator2.setName("jSeparator2"); // NOI18N

        cancelButton.setText(resourceMap.getString("cancelButton.text")); // NOI18N
        cancelButton.setName("cancelButton"); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jDialog1Layout = new javax.swing.GroupLayout(jDialog1.getContentPane());
        jDialog1.getContentPane().setLayout(jDialog1Layout);
        jDialog1Layout.setHorizontalGroup(
            jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jDialog1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel7)
                    .addGroup(jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(jSeparator1, javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jSeparator2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 208, Short.MAX_VALUE))
                    .addGroup(jDialog1Layout.createSequentialGroup()
                        .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(mediaComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 62, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jDialog1Layout.createSequentialGroup()
                        .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(resolutionComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 61, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jDialog1Layout.createSequentialGroup()
                        .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(audFormatComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 97, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jDialog1Layout.createSequentialGroup()
                        .addComponent(jLabel5, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(encodeStatComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 108, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jLabel1)
                    .addGroup(jDialog1Layout.createSequentialGroup()
                        .addComponent(releaseAllRadioButton, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(onlyNonIntRadioButton, javax.swing.GroupLayout.PREFERRED_SIZE, 111, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jDialog1Layout.createSequentialGroup()
                        .addComponent(onlyInternalRadioButton, javax.swing.GroupLayout.PREFERRED_SIZE, 89, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(internalComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jLabel6)
                    .addGroup(jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jDialog1Layout.createSequentialGroup()
                            .addComponent(allTypeRadioButton, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addComponent(onlyNonFreeRadioButton, javax.swing.GroupLayout.PREFERRED_SIZE, 121, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jDialog1Layout.createSequentialGroup()
                            .addGroup(jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(onlyFreeRadioButton, javax.swing.GroupLayout.PREFERRED_SIZE, 97, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGroup(jDialog1Layout.createSequentialGroup()
                                    .addGap(39, 39, 39)
                                    .addComponent(okButton)))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addGroup(jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(jDialog1Layout.createSequentialGroup()
                                    .addGap(10, 10, 10)
                                    .addComponent(cancelButton))
                                .addComponent(freeleechComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))))
                .addContainerGap())
        );
        jDialog1Layout.setVerticalGroup(
            jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jDialog1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE, false)
                    .addComponent(releaseAllRadioButton)
                    .addComponent(onlyNonIntRadioButton))
                .addGap(6, 6, 6)
                .addGroup(jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(onlyInternalRadioButton)
                    .addGroup(jDialog1Layout.createSequentialGroup()
                        .addGap(3, 3, 3)
                        .addComponent(internalComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.DEFAULT_SIZE, 8, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jDialog1Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(jLabel2))
                    .addComponent(mediaComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jDialog1Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(jLabel3))
                    .addComponent(resolutionComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jDialog1Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(jLabel4))
                    .addComponent(audFormatComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(12, 12, 12)
                .addGroup(jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(encodeStatComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator2, javax.swing.GroupLayout.DEFAULT_SIZE, 8, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel7)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE, false)
                    .addComponent(allTypeRadioButton)
                    .addComponent(onlyNonFreeRadioButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(onlyFreeRadioButton)
                    .addGroup(jDialog1Layout.createSequentialGroup()
                        .addGap(3, 3, 3)
                        .addComponent(freeleechComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(13, 13, 13)
                .addGroup(jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(okButton)
                    .addComponent(cancelButton))
                .addGap(12, 12, 12))
        );

        setComponent(mainPanel);
        setMenuBar(menuBar);
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        showAddDialog();
    }//GEN-LAST:event_jButton1ActionPerformed

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        DefaultTableModel dtm = (DefaultTableModel) jTable1.getModel();
        if (curr != null) {
            removeTableEntry(curr);
            if (releaseAllRadioButton.isSelected()) {
                curr.group = null;
                curr.onlyInternal = false;
                curr.onlyNonInternal = false;
            } else if (onlyInternalRadioButton.isSelected()) {
                curr.group = internalComboBox.getSelectedItem().toString();
                curr.onlyInternal = true;
                curr.onlyNonInternal = false;
            } else if (onlyNonIntRadioButton.isSelected()) {
                curr.group = null;
                curr.onlyInternal = false;
                curr.onlyNonInternal = true;
            }
            if (allTypeRadioButton.isSelected()) {
                curr.freeleech = null;
                curr.onlyFreeleech = false;
                curr.onlyNonFreeleech = false;
            } else if (onlyFreeRadioButton.isSelected()) {
                curr.freeleech = freeleechComboBox.getSelectedItem().toString();
                curr.onlyFreeleech = true;
                curr.onlyNonFreeleech = false;
            } else if (onlyNonFreeRadioButton.isSelected()) {
                curr.freeleech = null;
                curr.onlyFreeleech = false;
                curr.onlyNonFreeleech = true;
            }

            curr.audformat = audFormatComboBox.getSelectedItem().toString();
            curr.encodeStatus = encodeStatComboBox.getSelectedItem().toString();
            curr.media = mediaComboBox.getSelectedItem().toString();
            curr.mediaIcon = micon.get(mediaComboBox.getSelectedItem().toString());
            curr.resolution = resolutionComboBox.getSelectedItem().toString();

            dtm.addRow(new Object[]{curr});

            curr = null;

            JOptionPane.showMessageDialog(jDialog1,
                    "Movie saved sucessfully",
                    AHDDownloaderApp.getApplication().getName(),
                    JOptionPane.INFORMATION_MESSAGE);

            saveTableData();

            configurationChanged();
        } else {
            JOptionPane.showMessageDialog(jDialog1,
                    "Error when adding the movie",
                    AHDDownloaderApp.getApplication().getName(),
                    JOptionPane.ERROR_MESSAGE);
        }
        jDialog1.setVisible(false);
        addDialog.setVisible(false);
    }//GEN-LAST:event_okButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        jDialog1.setVisible(false);
        addDialog.setVisible(false);
        curr = null;
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        // TODO add your handling code here:
        if (jTable1.getSelectedRow() == -1) {
            return;
        }
        DefaultTableModel dtm = (DefaultTableModel) jTable1.getModel();
        curr = (WantedMovie) dtm.getValueAt(jTable1.getSelectedRow(), 0);

        if (curr.group == null && !curr.onlyInternal
                && !curr.onlyNonInternal) {
            releaseAllRadioButton.setSelected(true);
        } else if (curr.group != null && curr.onlyInternal
                && !curr.onlyNonInternal) {
            onlyInternalRadioButton.setSelected(true);
            internalComboBox.setSelectedItem(curr.group);
        } else if (curr.group == null && !curr.onlyInternal
                && curr.onlyNonInternal) {
            onlyNonIntRadioButton.setSelected(true);
        }

        if (curr.freeleech == null && !curr.onlyFreeleech
                && !curr.onlyNonFreeleech) {
            allTypeRadioButton.setSelected(true);
        } else if (curr.freeleech != null && curr.onlyFreeleech
                && !curr.onlyNonFreeleech) {
            onlyFreeRadioButton.setSelected(true);
            freeleechComboBox.setSelectedItem(curr.freeleech);
        } else if (curr.freeleech == null && !curr.onlyFreeleech
                && curr.onlyNonFreeleech) {
            onlyNonFreeRadioButton.setSelected(true);
        }

        audFormatComboBox.setSelectedItem(curr.audformat);
        encodeStatComboBox.setSelectedItem(curr.encodeStatus);
        mediaComboBox.setSelectedItem(curr.media);
        resolutionComboBox.setSelectedItem(curr.resolution);
        jDialog1.pack();
        jDialog1.setLocationRelativeTo(addDialog);
        jDialog1.setVisible(true);
    }//GEN-LAST:event_jButton3ActionPerformed

    private void saveBrowseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveBrowseButtonActionPerformed
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int showOpenDialog = fileChooser.showOpenDialog(getFrame());

        if (showOpenDialog == JFileChooser.APPROVE_OPTION) {
            saveDirectory = fileChooser.getSelectedFile();
            saveToTextField.setText(saveDirectory.toString());
            getSettings().put("saveDir", saveDirectory.getAbsolutePath());
            saveDataSilent();
            configurationChanged();
        }
}//GEN-LAST:event_saveBrowseButtonActionPerformed

    private void searchMinutesTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchMinutesTextFieldActionPerformed
        String text = searchMinutesTextField.getText();
        try {
            int mins = Integer.parseInt(text);
            if (mins <= 0) {
                JOptionPane.showMessageDialog(getFrame(),
                        "Number must be a positive integer.",
                        "Warning!", JOptionPane.WARNING_MESSAGE);
            } else {
                minutes = mins;
            }
            searchMinutesTextField.setText(minutes + "");
            getSettings().put("searchInterval", minutes + "");
            saveDataSilent();
            configurationChanged();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(getFrame(),
                    "Invalid number. Number must be an integer.",
                    "Error!", JOptionPane.ERROR_MESSAGE);
        }
}//GEN-LAST:event_searchMinutesTextFieldActionPerformed

    private void searchAtStartupCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchAtStartupCheckBoxActionPerformed
        getSettings().put("searchAtStartup",
                searchAtStartupCheckBox.isSelected() + "");
        saveDataSilent();
}//GEN-LAST:event_searchAtStartupCheckBoxActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        int row = jTable1.getSelectedRow();
        if (!(row >= 0 && row < jTable1.getRowCount())) {
            return;
        }
        WantedMovie theEntry = getTableEntry(row);
        int result =
                JOptionPane.showOptionDialog(this.getComponent(),
                "Do you really want to delete "
                + theEntry + "?", AHDDownloaderApp.getApplication().getName(),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, null, null);
        if (result == JOptionPane.YES_OPTION) {
            removeTableEntry(row);
            saveTableData();
        }
    }//GEN-LAST:event_jButton2ActionPerformed

    private int getRow(WantedMovie wm) {
        DefaultTableModel model = (DefaultTableModel) jTable1.getModel();
        int rows = model.getRowCount();
        int toRemove = -1;
        for (int i = 0; i < rows; i++) {
            Object o = model.getValueAt(i, 0);
            if (wm.equals(o)) {
                toRemove = i;
            }
        }
        return toRemove;
    }

    private WantedMovie getTableEntry(int position) {
        DefaultTableModel model = (DefaultTableModel) jTable1.getModel();
        return (WantedMovie) model.getValueAt(position, 0);
    }

    private void removeTableEntry(int position) {
        DefaultTableModel model = (DefaultTableModel) jTable1.getModel();
        model.removeRow(position);
    }

    private void removeTableEntry(WantedMovie wm) {
        synchronized (favs) {
            int row = getRow(wm);
            if (row != -1) {
                removeTableEntry(row);
            }
            favs.remove(wm);
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton allTypeRadioButton;
    private javax.swing.JComboBox audFormatComboBox;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.JButton cancelButton;
    private javax.swing.JComboBox encodeStatComboBox;
    private javax.swing.JLabel findLabel;
    private javax.swing.JComboBox freeleechComboBox;
    private javax.swing.JComboBox internalComboBox;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JDialog jDialog1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JSeparator jSeparator5;
    private javax.swing.JTable jTable1;
    private javax.swing.JToolBar jToolBar1;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JComboBox mediaComboBox;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JLabel nextCheckLabel;
    private javax.swing.JButton okButton;
    private javax.swing.JRadioButton onlyFreeRadioButton;
    private javax.swing.JRadioButton onlyInternalRadioButton;
    private javax.swing.JRadioButton onlyNonFreeRadioButton;
    private javax.swing.JRadioButton onlyNonIntRadioButton;
    private javax.swing.JRadioButton releaseAllRadioButton;
    private javax.swing.JComboBox resolutionComboBox;
    private javax.swing.JButton saveBrowseButton;
    private javax.swing.JLabel saveToLabel;
    private javax.swing.JTextField saveToTextField;
    private javax.swing.JCheckBox searchAtStartupCheckBox;
    private javax.swing.JLabel searchEpisodesLabel;
    private javax.swing.JLabel searchMinutesLabel;
    private javax.swing.JTextField searchMinutesTextField;
    // End of variables declaration//GEN-END:variables
    private JDialog aboutBox;
    private JDialog addDialog;
    private File saveDirectory;
    private int minutes;
    private boolean searchAtStartup;
    private Timer timer;
    private AHDDownloaderTask currentTask;
    private TrayIconStreamed trayIcon;
    protected Ticker tiker;

    private void saveData() throws FileNotFoundException, IOException,
            IllegalArgumentException, IllegalAccessException {
        AHDDownloaderApp.getApplication().saveData();
    }

    private void saveDataSilent() {
        try {
            saveData();
        } catch (Exception ex) {
        }
    }

    private Properties getSettings() {
        return AHDDownloaderApp.getApplication().getSettings();
    }

    private void configurationChanged() {
        if (currentTask != null) {
            tiker.remove(currentTask);
            //tiker.cancel();
            timer.purge();
        }
        if (saveDirectory == null) {
            return;
        }
        RSSFeed feed = null;
        try {
            feed = new RSSFeed(new URL("http://awesome-hd.net/feeds.php?"
                    + "feed=torrents_movies&user=349&auth=1356fe8ef109b9ba"
                    + "f4a05a7ece0798d9&passkey=a6di0mfk1i09f1qehxtnmvd4j0"
                    + "ata332&authkey=2e0948350f7fab4f3ce0bb4925b29774"),
                    RSSFeed.Type.DEFINED);
        } catch (MalformedURLException ex) {
        }
        currentTask = new AHDDownloaderTask(
                new AHDRSSTorrentDownloader(favs, feed, internals,
                saveDirectory), this);
        //currentTask.getLogger().addHandler(sh);
        //currentTask.getLogger().setLevel(current);

        tiker.setTick(minutes);
        tiker.add(currentTask);
    }
    private List<WantedMovie> favs;

    public static String getIMDBID(String search)
            throws UnsupportedEncodingException,
            MalformedURLException, IOException {
        if (search == null) {
            return null;
        }
        String imdb = null;
        search = search.replace(":", "");
        search = URLEncoder.encode(search, "UTF-8");
        //System.out.println(search);
        URL url = new URL("http://www.imdb.com/find?s=tt&q="
                + search);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows; U; "
                + "Windows NT 6.0; en-GB; rv:1.9.1.2) "
                + "Gecko/20090729 "
                + "Firefox/3.5.2 (.NET CLR 3.5.30729)");
        net.htmlparser.jericho.Source source =
                new net.htmlparser.jericho.Source(conn.getInputStream());
        source.setLogger(null);
        String x = source.toString();
        int l = x.indexOf("div id=\"main\"");
        if (l >= 0) {
            x = x.substring(l);
            Pattern pattern2 = Pattern.compile("tt\\d{7}");
            Matcher matcher = pattern2.matcher(x);
            if (matcher.find()) {
                /*System.out.println(*/                imdb = matcher.group(0)/*)*/;
            }
        } else {
            l = x.indexOf("<head>");
            if (l >= 0) {
                x = x.substring(l);
                Pattern pattern2 = Pattern.compile("tt\\d{7}");
                Matcher matcher = pattern2.matcher(x);
                if (matcher.find()) {
                    /*System.out.println(*/                    imdb = matcher.group(0)/*)*/;
                }
            }
        }
        if (imdb != null) {
            imdb = imdb.substring(2);
        }
        return imdb;
    }

    private void saveTableData() {
        try {
            DefaultTableModel dtm = (DefaultTableModel) jTable1.getModel();
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(
                    new File(AHDDownloaderApp.getApplication().getSettingsDirectory(),
                    "data.db")));
            oos.writeObject(dtm.getDataVector());
        } catch (Exception e) {
        }
    }
}
