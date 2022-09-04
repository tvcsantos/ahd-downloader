/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ahddownloader;

import ahddownloader.AHDDownloaderView.WantedMovie;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/**
 *
 * @author tvcsantos
 */
public class WantedMovieTableCellRender extends JLabel implements TableCellRenderer {

    private static final Font SMALL_FONT = new Font("Dialog", 0, 10);
    private static final Font LARGE_FONT = new Font("Dialog", 0, 15);

    private Color evenRowColor = Color.WHITE;
    private Color oddRowColor = new Color(236, 243, 254);
    private Color selectedRowColor = new Color(61, 128, 223);
    private JPanel currentPanel = new JPanel();
    private JLabel nameLabel = new JLabel();
    private JLabel iconLabel = new JLabel();

    private JLabel mediaLabel = new JLabel();
    private JLabel resolutionLabel = new JLabel();
    private JLabel audioFormatLabel = new JLabel();
    private JLabel encodeStatusLabel = new JLabel();
    private JLabel groupLabel = new JLabel();

    private JLabel rgroup = new JLabel();
    private JLabel media = new JLabel();
    private JLabel resol = new JLabel();
    private JLabel audform = new JLabel();
    private JLabel encstat = new JLabel();

    private Map<String, Image> mapImg = new HashMap<String, Image>();

    public WantedMovieTableCellRender() {
        super();
    }

    public WantedMovieTableCellRender(File imgSaveDir) {
        super();
    }
    
    public static BufferedImage resize(BufferedImage img, int newW, int newH) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage dimg = dimg = new BufferedImage(newW, newH,
                img.getType());
        Graphics2D g = dimg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, newW, newH, 0, 0, w, h, null);
        g.dispose();
        return dimg;
    }

    private int xPos = 0;
    private int yPos = 0;
    private int nTabs = 2;
    private int currTab = 1;

    public void advanceTab() {
        Dimension preferredSize = currentPanel.getPreferredSize();
        int realWidth = (currentPanel.getWidth() - 80);
        xPos = currTab*(realWidth/nTabs);
        yPos = 25;
    }

    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        WantedMovie wm = (WantedMovie) value;
        currentPanel.setLayout(null);

        currentPanel.setBackground(colorForRow(row, isSelected));

        //JLabel name = new JLabel();
        nameLabel.setText(wm + "");
        nameLabel.setFont(LARGE_FONT);
        nameLabel.setForeground(getFontColor(Color.BLACK, isSelected));
        nameLabel.setBounds(80, 3, nameLabel.getPreferredSize().width,
                nameLabel.getPreferredSize().height);
        currentPanel.add(nameLabel);

        try {
            
            if (wm.imgLink != null) {

                Image im = mapImg.get(wm.imgLink);
                if (im != null) iconLabel.setIcon(new ImageIcon(im));
                else  {
                BufferedImage img = ImageIO.read(new URL(wm.imgLink));
                img = resize(img, (int)(img.getWidth()*0.3),
                        (int)(img.getHeight()*0.3));
                iconLabel.setIcon(new ImageIcon(img));
                mapImg.put(wm.imgLink, img);
                }
            }

            iconLabel.setBounds(6, 4, iconLabel.getPreferredSize().width,
                iconLabel.getPreferredSize().height);
            currentPanel.add(iconLabel);

            xPos = 80;
            yPos = 25;

            rgroup.setFont(SMALL_FONT.deriveFont(Font.BOLD));
            rgroup.setText("Release Group:");
            media.setFont(SMALL_FONT.deriveFont(Font.BOLD));
            media.setText("Media:");
            resol.setFont(SMALL_FONT.deriveFont(Font.BOLD));
            resol.setText("Resolution:");
            audform.setFont(SMALL_FONT.deriveFont(Font.BOLD));
            audform.setText("Audio Format:");
            encstat.setFont(SMALL_FONT.deriveFont(Font.BOLD));
            encstat.setText("Encode Status:");
            mediaLabel.setFont(SMALL_FONT);
            resolutionLabel.setFont(SMALL_FONT);          
            audioFormatLabel.setFont(SMALL_FONT);
            encodeStatusLabel.setFont(SMALL_FONT);
            groupLabel.setFont(SMALL_FONT);

            placeLabel(rgroup, currentPanel, false);

            if (wm.onlyInternal)
                groupLabel.setText(wm.group);
            else if (wm.onlyNonInternal)
                groupLabel.setText("Non-Internal");
            else groupLabel.setText("All");
            
            placeLabel(groupLabel,currentPanel, true);

            placeLabel(media, currentPanel, false);

            mediaLabel.setText(wm.media);

            placeLabel(mediaLabel, currentPanel, true);

            placeLabel(resol, currentPanel, false);

            resolutionLabel.setText(wm.resolution);

            placeLabel(resolutionLabel, currentPanel, false);

            advanceTab();

            placeLabel(audform, currentPanel, false);

            audioFormatLabel.setText(wm.audformat);

            placeLabel(audioFormatLabel, currentPanel, true);

            placeLabel(encstat, currentPanel, false);

            encodeStatusLabel.setText(wm.encodeStatus);

            placeLabel(encodeStatusLabel, currentPanel, false);


        } catch (IOException ex) {
            
        }
      
        return currentPanel;
    }

    /**
     * Returns the appropriate background color for the given row.
     */
    protected Color colorForRow(int row, boolean isSelected) {
        if (isSelected) {
            return selectedRowColor;
        }
        if ((row % 2) == 0) {
            return evenRowColor;
        } else {
            return oddRowColor;
        }
    }

    /**
     * @param color Current font color
     * @param isSelected If font is selected
     * @return Font color for text
     */
    private Color getFontColor(Color color, boolean isSelected) {
        if (isSelected) {
            return Color.WHITE;
        } else {
            return color;
        }
    }

    private void placeLabel(JLabel label,
             JPanel container, boolean newLine) {
        label.setBounds(xPos, yPos,
                label.getPreferredSize().width,
                label.getPreferredSize().height);
        container.add(label);
        if (newLine) {
            xPos = 80;
            yPos += label.getHeight() + 1;
        } else {
            xPos += label.getWidth() + 5;
        }
    }

}
