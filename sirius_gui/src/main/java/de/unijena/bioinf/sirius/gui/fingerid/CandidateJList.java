/*
 *  This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2015 Kai Dührkop
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.unijena.bioinf.sirius.gui.fingerid;

import de.unijena.bioinf.chemdb.DatasourceService;
import de.unijena.bioinf.sirius.gui.configs.ConfigStorage;
import de.unijena.bioinf.sirius.gui.dialogs.ExceptionDialog;
import de.unijena.bioinf.sirius.gui.dialogs.FilePresentDialog;
import de.unijena.bioinf.sirius.gui.filefilter.SupportedExportCSVFormatsFilter;
import de.unijena.bioinf.sirius.gui.structure.ReturnValue;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.layout.StructureDiagramGenerator;
import org.openscience.cdk.renderer.AtomContainerRenderer;
import org.openscience.cdk.renderer.font.AWTFontManager;
import org.openscience.cdk.renderer.generators.*;
import org.openscience.cdk.renderer.visitor.AWTDrawVisitor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.text.AttributedCharacterIterator;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

public class CandidateJList extends JPanel implements MouseListener, ActionListener {

    private final static int CELL_SIZE = 15;
    private static final int MIN_CELL_SIZE = 3;

    protected CSIFingerIdComputation computation;
    private ConfigStorage config;
    protected FingerIdData data;
    protected JList<CompoundCandidate> candidateList;
    protected StructureSearcher structureSearcher;
    protected Thread structureSearcherThread;

    protected Font nameFont, propertyFont, rankFont, scoreSuperscriptFont;
    protected Frame owner;

    protected JMenuItem CopyInchiKey, CopyInchi, OpenInBrowser1, OpenInBrowser2;
    protected JPopupMenu popupMenu;

    protected int highlightMissing = -1, highlightAgree = -1, highlightedCandidate = -1;
    protected int selectedCompoundId;
    protected HashSet<String> logPCalculated = new HashSet<>();

    protected FilterPanel filterPanel;

    protected LogPSlider logPSlider;

    protected void initFonts() {
        try {
            InputStream fontFile = getClass().getResourceAsStream("/ttf/DejaVuSans-Bold.ttf");
            Font tempFont = null;
            tempFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);
            nameFont = tempFont.deriveFont(13f);
            propertyFont = tempFont.deriveFont(16f);
            rankFont = tempFont.deriveFont(32f);
            final HashMap<AttributedCharacterIterator.Attribute, Object> attrs = new HashMap<>();
            attrs.put(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUPER);
            attrs.put(TextAttribute.SIZE, 15f);
            scoreSuperscriptFont = nameFont.deriveFont(attrs);
        } catch (FontFormatException | IOException e) {
            nameFont = propertyFont = rankFont = Font.getFont(Font.SANS_SERIF);
        }
    }

    public CandidateJList(Frame owner, CSIFingerIdComputation computation, ConfigStorage config, FingerIdData data) {
        this.computation = computation;
        this.config = config;
        this.owner = owner;
        initFonts();
        setLayout(new BorderLayout());
        this.data = data;

        JPanel northPanels = new JPanel(new BorderLayout());
        add(northPanels, BorderLayout.NORTH);

        JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));

        northPanels.add(northPanel, BorderLayout.NORTH);
        filterPanel = new FilterPanel();
        filterPanel.toggle();
        filterPanel.whenFilterChanges(new Runnable() {
            @Override
            public void run() {
                updateFilter();
            }
        });
        northPanels.add(filterPanel, BorderLayout.SOUTH);

        final JButton exportToCSV = new JButton("export list", new ImageIcon(CandidateJList.class.getResource("/icons/document-export.png")));
        exportToCSV.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doExport();
            }
        });

        northPanel.add(exportToCSV);

        final ImageIcon downIcon = new ImageIcon(CandidateJList.class.getResource("/icons/filter_down.png"));
        final ImageIcon upIcon = new ImageIcon(CandidateJList.class.getResource("/icons/filter_up.png"));
        final JButton filter = new JButton("filter list",  downIcon);
        filter.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (filterPanel.toggle()) {
                    filter.setIcon(upIcon);
                } else {
                    filter.setIcon(downIcon);
                }
            }
        });

        northPanel.add(filter);

        logPSlider = new LogPSlider();
        northPanel.add(Box.createHorizontalGlue());
        northPanel.add(new JLabel("XLogP filter: "));
        northPanel.add(logPSlider);
        logPSlider.setCallback(new Runnable() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        updateFilter();
                    }
                });
            }
        });


        candidateList = new InnerList(new ListModel());
        ToolTipManager.sharedInstance().registerComponent(candidateList);
        candidateList.setCellRenderer(new CandidateCellRenderer());
        candidateList.setFixedCellHeight(-1);
        candidateList.setPrototypeCellValue(new CompoundCandidate(Compound.getPrototypeCompound(), 0d, 1, 0));
        final JScrollPane scrollPane = new JScrollPane(candidateList, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);

        candidateList.addMouseListener(this);
        this.structureSearcher = new StructureSearcher(computation, data == null ? 0 : data.compounds.length);
        this.structureSearcherThread = new Thread(structureSearcher);
        structureSearcherThread.start();
        this.structureSearcher.reloadList((ListModel) candidateList.getModel());

        ///// add popup menu
        popupMenu = new JPopupMenu();
        CopyInchiKey = new JMenuItem("Copy 2D InChIKey");
        CopyInchi = new JMenuItem("Copy 2D InChI");
        OpenInBrowser1 = new JMenuItem("Open in PubChem");
        OpenInBrowser2 = new JMenuItem("Open in all databases");
        CopyInchi.addActionListener(this);
        CopyInchiKey.addActionListener(this);
        OpenInBrowser1.addActionListener(this);
        OpenInBrowser2.addActionListener(this);
        popupMenu.add(CopyInchiKey);
        popupMenu.add(CopyInchi);
        popupMenu.add(OpenInBrowser1);
        popupMenu.add(OpenInBrowser2);
        setVisible(true);

    }

    public void updateFilter() {
        final ListModel model = (ListModel)candidateList.getModel();
        model.change();
    }


    @Override
    public void actionPerformed(ActionEvent e) {
        if (selectedCompoundId < 0) return;
        final CompoundCandidate c = candidateList.getModel().getElementAt(selectedCompoundId);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        if (e.getSource() == CopyInchiKey) {
            clipboard.setContents(new StringSelection(c.compound.inchi.key2D()), null);
        } else if (e.getSource() == CopyInchi) {
            clipboard.setContents(new StringSelection(c.compound.inchi.in2D), null);
        } else if (e.getSource() == OpenInBrowser1) {
            try {
                Desktop.getDesktop().browse(new URI("https://www.ncbi.nlm.nih.gov/pccompound?term=%22" + c.compound.inchi.key2D() + "%22[InChIKey]"));
            } catch (IOException | URISyntaxException e1) {
                e1.printStackTrace();
            }
        } else if (e.getSource() == OpenInBrowser2) {
            if (c.compound.databases == null) return;
            for (Map.Entry<String, String> entry : c.compound.databases.entries()) {
                final DatasourceService2.Sources s = DatasourceService2.getFromName(entry.getKey());
                if (entry.getValue() == null || s == null || s.URI == null) continue;
                try {
                    if (s.URI.contains("%s")) {
                        Desktop.getDesktop().browse(new URI(String.format(Locale.US, s.URI, URLEncoder.encode(entry.getValue(), "UTF-8"))));
                    } else {
                        Desktop.getDesktop().browse(new URI(String.format(Locale.US, s.URI, Integer.parseInt(entry.getValue()))));
                    }
                } catch (IOException | URISyntaxException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    private void doExport() {
        JFileChooser jfc = new JFileChooser();
        jfc.setCurrentDirectory(config.getDefaultTreeExportPath());
        jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        jfc.setAcceptAllFileFilterUsed(false);
        FileFilter csvFileFilter = new SupportedExportCSVFormatsFilter();
        jfc.addChoosableFileFilter(csvFileFilter);
        File selectedFile = null;
        while (selectedFile == null) {
            int returnval = jfc.showSaveDialog(this);
            if (returnval == JFileChooser.APPROVE_OPTION) {
                File selFile = jfc.getSelectedFile();
                if (!selFile.getName().endsWith(".csv")) selFile = new File(selectedFile.getAbsolutePath() + ".csv");

                config.setDefaultCompoundsExportPath(selFile.getParentFile());

                String name = selFile.getName();
                if (selFile.exists()) {
                    FilePresentDialog fpd = new FilePresentDialog(owner, selFile.getName());
                    ReturnValue rv = fpd.getReturnValue();
                    if (rv == ReturnValue.Success) {
                        selectedFile = selFile;
                    }
                } else {
                    selectedFile = selFile;
                }
            } else {
                break;
            }
        }

        if (selectedFile != null) {

            try {
                new CSVExporter().exportToFile(selectedFile, data);
            } catch (Exception e2) {
                ExceptionDialog fed = new ExceptionDialog(owner, e2.getMessage());
                e2.printStackTrace();
            }
        }
    }

    public void dispose() {
        structureSearcher.stop();
    }

    public void refresh(FingerIdData data) {
        this.data = data;
        this.filterPanel.setActiveExperiment(data);
        ((ListModel) candidateList.getModel()).change();
        this.structureSearcher.reloadList((ListModel) candidateList.getModel());
        this.logPSlider.refresh(data);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.isPopupTrigger()) return;
        highlightedCandidate = -1;
        highlightAgree = -1;
        highlightMissing = -1;
        final Point point = e.getPoint();
        final int index = candidateList.locationToIndex(point);
        selectedCompoundId = index;
        if (index < 0) return;
        final CompoundCandidate candidate = candidateList.getModel().getElementAt(index);
        highlightedCandidate = candidate.index;
        final Rectangle relativeRect = candidateList.getCellBounds(index, index);
        final boolean in;
        int rx, ry;
        {
            final Rectangle box = candidate.agreement.getBounds();
            final int absX = box.x + relativeRect.x;
            final int absY = box.y + relativeRect.y;
            final int absX2 = box.width + absX;
            final int absY2 = box.height + absY;
            in = point.x >= absX && point.y >= absY && point.x < absX2 && point.y < absY2;
            rx = point.x - absX;
            ry = point.y - absY;
        }
        if (in) {
            final int row = ry / CELL_SIZE;
            final int col = rx / CELL_SIZE;
            highlightAgree = candidate.agreement.indexAt(row, col);
            structureSearcher.reloadList((ListModel) candidateList.getModel(), highlightAgree, highlightedCandidate);
        } else {
            final Rectangle box = candidate.missings.getBounds();
            final int absX = box.x + relativeRect.x;
            final int absY = box.y + relativeRect.y;
            final int absX2 = box.width + absX;
            final int absY2 = box.height + absY;
            if (point.x >= absX && point.y >= absY && point.x < absX2 && point.y < absY2) {
                rx = point.x - absX;
                ry = point.y - absY;
                final int row = ry / CELL_SIZE;
                final int col = rx / CELL_SIZE;
                highlightMissing = candidate.missings.indexAt(row, col);
                structureSearcher.reloadList((ListModel) candidateList.getModel(), highlightMissing, highlightedCandidate);
            } else {
                if (highlightAgree >= 0) {
                    highlightAgree = -1;
                    structureSearcher.reloadList((ListModel) candidateList.getModel(), highlightAgree, highlightedCandidate);
                }
                if (highlightMissing >= 0) {
                    highlightMissing = -1;
                    structureSearcher.reloadList((ListModel) candidateList.getModel(), highlightMissing, highlightedCandidate);
                }

                double rpx = point.x - relativeRect.getX(), rpy = point.y - relativeRect.getY();
                for (de.unijena.bioinf.sirius.gui.fingerid.DatabaseLabel l : candidate.labels) {
                    if (l.rect.contains(rpx, rpy)) {
                        clickOnDBLabel(l);
                        break;
                    }
                }

            }
        }
    }

    private void clickOnDBLabel(de.unijena.bioinf.sirius.gui.fingerid.DatabaseLabel label) {
        final DatasourceService2.Sources s = DatasourceService2.getFromName(label.name);
        if (label.values == null || label.values.length==0 || s == null || s.URI == null) return;
        try {
            for (String id : label.values) {
                if (id==null) continue;
                if (s.URI.contains("%s")) {
                    Desktop.getDesktop().browse(new URI(String.format(Locale.US, s.URI, URLEncoder.encode(id, "UTF-8"))));
                } else {
                    Desktop.getDesktop().browse(new URI(String.format(Locale.US, s.URI, Integer.parseInt(id))));
                }
            }
        } catch (IOException | URISyntaxException e1) {
            e1.printStackTrace();
        }
    }

    public class InnerList extends JList<CompoundCandidate> {

        public InnerList(ListModel dataModel) {
            super(dataModel);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            final Point point = e.getPoint();
            final int index = candidateList.locationToIndex(point);
            selectedCompoundId = index;
            if (index < 0) return null;
            final CompoundCandidate candidate = candidateList.getModel().getElementAt(index);
            highlightedCandidate = candidate.rank;
            final Rectangle relativeRect = candidateList.getCellBounds(index, index);
            final boolean in;
            int rx, ry;
            {
                final Rectangle box = candidate.agreement.getBounds();
                final int absX = box.x + relativeRect.x;
                final int absY = box.y + relativeRect.y;
                final int absX2 = box.width + absX;
                final int absY2 = box.height + absY;
                in = point.x >= absX && point.y >= absY && point.x < absX2 && point.y < absY2;
                rx = point.x - absX;
                ry = point.y - absY;
            }
            int fpindex = -1;
            if (in) {
                final int row = ry / CELL_SIZE;
                final int col = rx / CELL_SIZE;
                fpindex = candidate.agreement.indexAt(row, col);
            } else {
                final Rectangle box = candidate.missings.getBounds();
                final int absX = box.x + relativeRect.x;
                final int absY = box.y + relativeRect.y;
                final int absX2 = box.width + absX;
                final int absY2 = box.height + absY;
                if (point.x >= absX && point.y >= absY && point.x < absX2 && point.y < absY2) {
                    rx = point.x - absX;
                    ry = point.y - absY;
                    final int row = ry / CELL_SIZE;
                    final int col = rx / CELL_SIZE;
                    fpindex = candidate.missings.indexAt(row, col);
                }
            }
            if (fpindex >= 0) {
                return candidate.compound.fingerprint.getFingerprintVersion().getMolecularProperty(fpindex).getDescription();
            } else return null;

        }
    }

    private void popup(MouseEvent e, CompoundCandidate candidate) {
        popupMenu.show(candidateList, e.getX(), e.getY());
    }

    @Override
    public void mousePressed(MouseEvent e) {
        highlightedCandidate = -1;
        highlightAgree = -1;
        highlightMissing = -1;
        final Point point = e.getPoint();
        final int index = candidateList.locationToIndex(point);
        selectedCompoundId = index;
        if (index < 0) return;
        final CompoundCandidate candidate = candidateList.getModel().getElementAt(index);
        highlightedCandidate = candidate.rank;
        if (e.isPopupTrigger()) {
            popup(e, candidate);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        highlightedCandidate = -1;
        highlightAgree = -1;
        highlightMissing = -1;
        final Point point = e.getPoint();
        final int index = candidateList.locationToIndex(point);
        selectedCompoundId = index;
        if (index < 0) return;
        final CompoundCandidate candidate = candidateList.getModel().getElementAt(index);
        highlightedCandidate = candidate.rank;
        if (e.isPopupTrigger()) {
            popup(e, candidate);
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }

    public class ListModel extends AbstractListModel<CompoundCandidate> {
        ArrayList<CompoundCandidate> candidates;

        public ListModel() {
            this.candidates = new ArrayList<>();
            change();
        }

        @Override
        public int getSize() {
            return candidates.size();
        }

        public void refreshList() {
            fireContentsChanged(this, 0, getSize());
        }

        public void change(int elem) {
            fireContentsChanged(this, elem, elem);
        }

        public void change(int from, int to) {
            fireContentsChanged(this, from, to);
        }

        public void change() {
            if (data != null) {
                candidates.clear();
                int toFlag = 0;
                final double minValue = data.minLogPFilter, maxValue = data.maxLogPFilter;
                if (data.dbSelection.contains(DatasourceService.Sources.PUBCHEM)) toFlag = -1;
                else for (DatasourceService.Sources s : data.dbSelection) toFlag |= s.flag;
                System.out.println(toFlag);
                for (int i = 0; i < data.compounds.length; ++i) {
                    if (toFlag<0 || (toFlag & data.compounds[i].bitset)!=0) {
                        double logp=data.compounds[i].xlogP;
                        if (!Double.isNaN(logp) && logp >= minValue && logp <= maxValue) {
                            candidates.add(new CompoundCandidate(data.compounds[i], data.scores[i], i + 1, i));
                        }
                    }
                }
            } else candidates = new ArrayList<>();
            refreshList();
        }

        @Override
        public CompoundCandidate getElementAt(int index) {
            return candidates.get(index);
        }
    }

    public class CandidateCellRenderer extends JPanel implements ListCellRenderer<CompoundCandidate> {

        private CompoundImage image;
        private Color even, odd;
        private DescriptionPanel descriptionPanel;
        private CompoundCandidate currentCandidate;

        public CandidateCellRenderer() {
            setLayout(new BorderLayout());
            image = new CompoundImage();
            descriptionPanel = new DescriptionPanel();
            even = Color.WHITE;
            odd = new Color(213, 227, 238);
            add(image, BorderLayout.WEST);
            add(descriptionPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends CompoundCandidate> list, CompoundCandidate value, int index, boolean isSelected, boolean cellHasFocus) {
            image.molecule = value;
            image.backgroundColor = (index % 2 == 0 ? even : odd);
            setOpaque(true);
            setBackground(image.backgroundColor);
            descriptionPanel.setCompound(value);
            currentCandidate = value;
            return this;
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
            // memoize coordinates of agreement boxes
            final Rectangle ra = descriptionPanel.ag.getBounds();
            final Rectangle rv = descriptionPanel.vio.getBounds();
            // add offset of parents
            ra.setLocation(ra.x + descriptionPanel.getX(), ra.y + descriptionPanel.getY());
            rv.setLocation(rv.x + descriptionPanel.getX(), rv.y + descriptionPanel.getY());
            ra.setLocation(ra.x + descriptionPanel.agpanel.getX(), ra.y + descriptionPanel.agpanel.getY());
            rv.setLocation(rv.x + descriptionPanel.viopanel.getX(), rv.y + descriptionPanel.viopanel.getY());

            currentCandidate.agreement.setBounds(ra.x, ra.y, ra.width, ra.height);
            currentCandidate.missings.setBounds(rv.x, rv.y, rv.width, rv.height);
        }
    }

    private class CompoundImage extends JPanel {

        protected CompoundCandidate molecule;
        protected AtomContainerRenderer renderer;
        protected Color backgroundColor;

        public CompoundImage() {
            setOpaque(false);
            setPreferredSize(new Dimension(374, 215));
            // make generators
            java.util.List<IGenerator<IAtomContainer>> generators = new ArrayList<IGenerator<IAtomContainer>>();
            generators.add(new BasicSceneGenerator());
            generators.add(new BasicBondGenerator());
            generators.add(new RingGenerator());
            generators.add(new BasicAtomGenerator());
            generators.add(new HighlightGenerator());

            // setup the renderer
            this.renderer = new AtomContainerRenderer(generators, new AWTFontManager());
            renderer.getRenderer2DModel().set(HighlightGenerator.HighlightPalette.class,
                    HighlightGenerator.createPalette(new Color(0, 100, 255, 128), new Color(100, 100, 255, 64).brighter()));
            setVisible(true);
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
            if (molecule.compound.molecule != null) {
                final Graphics2D gg = (Graphics2D) g;
                StructureDiagramGenerator sdg = new StructureDiagramGenerator();
                sdg.setMolecule(molecule.compound.getMolecule(), false);
                try {
                    sdg.generateCoordinates();
                } catch (CDKException e) {
                    e.printStackTrace();
                }
                renderer.getRenderer2DModel().set(BasicSceneGenerator.BackgroundColor.class, backgroundColor);
                synchronized (molecule.compound) {
                    renderer.paint(molecule.compound.getMolecule(), new AWTDrawVisitor(gg),
                            new Rectangle2D.Double(7, 14, 360, 185), true);
                }
                if (molecule.compound.name != null) {
                    gg.setFont(nameFont);
                    gg.drawString(molecule.compound.name, 3, 16);
                }
                gg.setFont(rankFont);
                final String rankString = String.valueOf(molecule.rank);
                final Rectangle2D bound = gg.getFontMetrics().getStringBounds(rankString, gg);
                {
                    final int x = 3;
                    final int y = getHeight() - (int) (bound.getHeight());
                    final int h = (int) (y + bound.getHeight());
                    gg.drawString(rankString, x, h - 2);
                }
                gg.setFont(nameFont);
                final String scoreText1 = "score: e", scoreText2 = String.format(Locale.US, "%d", (long) Math.round(molecule.score));
                double w = gg.getFontMetrics(nameFont).getStringBounds(scoreText1, gg).getWidth(),
                        w2 = gg.getFontMetrics(scoreSuperscriptFont).getStringBounds(scoreText2, gg).getWidth();
                /*{
                    Color from = new Color(backgroundColor.getRed(),backgroundColor.getGreen(),backgroundColor.getBlue(),0);
                    Color to = new Color(backgroundColor.getRed(),backgroundColor.getGreen(),backgroundColor.getBlue(),255);

                    int xx = (int)(getWidth()-(w + w2)), yy = (int)(getHeight()-30);
                    int mid = xx + (getWidth()-xx)/2;
                    GradientPaint paint = new GradientPaint(mid, yy, from,
                            mid, yy+15, to, false);
                    Paint oldPaint = gg.getPaint();
                    gg.setPaint(paint);
                    gg.fillRect(xx, yy, getWidth()-xx, getHeight()-yy);
                    gg.setPaint(oldPaint);
                }*/
                gg.drawString(scoreText1, (int) (getWidth() - (w + w2 + 4)), getHeight() - 4);
                gg.setFont(scoreSuperscriptFont);
                gg.drawString(scoreText2, (int) (getWidth() - (w2 + 4)), getHeight() - 4);
            }
        }
    }

    public class FingerprintView extends JPanel {

        private FingerprintAgreement agreement;
        Color color;

        public FingerprintView(int height, Color color) {
            this.color = color;
            setOpaque(false);
            setPreferredSize(new Dimension(Integer.MAX_VALUE, height));
        }

        public void setAgreement(FingerprintAgreement agreement) {
            this.agreement = agreement;
            final int numberOfCols = Math.min(agreement.indizes.length, (getWidth() - 2) / CELL_SIZE);
            final int numberOfRows = numberOfCols == 0 ? 1 : ((agreement.indizes.length + numberOfCols - 1) / numberOfCols);
            agreement.setNumberOfCols(numberOfCols);
            final int W = numberOfCols * CELL_SIZE;
            final int H = numberOfRows * CELL_SIZE;
            //setPreferredSize(new Dimension(Integer.MAX_VALUE, H + 8));
            //revalidate();
        }

        @Override
        public void paint(Graphics graphics) {
            super.paint(graphics);
            if (agreement == null || agreement.indizes.length == 0) return;
            final Graphics2D g = (Graphics2D) graphics;
            final int numberOfCols = Math.min(agreement.indizes.length, (getWidth() - 2) / CELL_SIZE);
            final int numberOfRows = ((agreement.indizes.length + numberOfCols - 1) / numberOfCols);
            agreement.setNumberOfCols(numberOfCols);
            final int W = numberOfCols * CELL_SIZE;
            final int H = numberOfRows * CELL_SIZE;
            final int sizeOfLastRow = agreement.indizes.length % numberOfCols;
            /*
            g.setColor(Color.BLACK);
            if (sizeOfLastRow==0) {
                g.fillRect(0, 0, W, H);
            } else {
                g.fillRect(0, 0, W, H- CELL_SIZE);
                g.fillRect(0, CELL_SIZE *(numberOfRows-1), CELL_SIZE *sizeOfLastRow, CELL_SIZE);
            }
            */
            final float[] components = new float[3];
            Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), components);

            // highlight current INDEX

            final int useable_cell_size = CELL_SIZE - 1;
            for (int i = 0; i < agreement.indizes.length; ++i) {
                final float weight = (float) agreement.weights[i];
                final int row = i / numberOfCols;
                final int col = i % numberOfCols;

                final double weight2 = Math.max(0.25, agreement.weights2[i]);
                final int reduction = (int) Math.round((useable_cell_size - (((useable_cell_size - MIN_CELL_SIZE) / 0.75) * weight2)) / 2d) + 2;
                final int b;
                if (agreement.indizes[i] == highlightAgree || agreement.indizes[i] == highlightMissing) {
                    g.setColor(Color.BLUE);
                    b = 2;
                } else {
                    b = 1;
                    g.setColor(Color.BLACK);
                }
                g.fillRect((CELL_SIZE * col) + reduction - b, (CELL_SIZE * row) + reduction - b, (CELL_SIZE - reduction - reduction) + b + b, (CELL_SIZE - reduction - reduction) + b + b);

                g.setColor(Color.getHSBColor(components[0], components[1], weight));
                g.fillRect(reduction + CELL_SIZE * col, reduction + CELL_SIZE * row, CELL_SIZE - reduction - reduction, CELL_SIZE - reduction - reduction);
            }


        }
    }

    private static final int DB_LABEL_PADDING = 4;

    public class DatabasePanel extends JPanel {
        private CompoundCandidate candidate;
        private Font ownFont;
        private Color bgColor = new Color(155, 166, 219);

        public DatabasePanel() {
            setOpaque(false);
            setLayout(new FlowLayout(FlowLayout.LEFT));
            setBorder(new EmptyBorder(5, 2, 2, 2));
            ownFont = getFont().deriveFont(Font.BOLD, 12);
        }

        public void setCompound(CompoundCandidate candidate) {
            removeAll();
            if (candidate == null || candidate.compound == null || candidate.compound.databases == null) return;
            final ArrayList<String> dbNames = new ArrayList<>(candidate.compound.databases.keySet());
            Collections.sort(dbNames);
            final FontMetrics m = getFontMetrics(ownFont);
            List<de.unijena.bioinf.sirius.gui.fingerid.DatabaseLabel> labels = new ArrayList<>();

            final Rectangle2D boundary = getBounds();

            for (de.unijena.bioinf.sirius.gui.fingerid.DatabaseLabel label : candidate.labels) {
                final TextLayout tlayout = new TextLayout(label.name, ownFont, new FontRenderContext(null, false, false));
                final Rectangle2D r = tlayout.getBounds();
                final int X = (int) r.getWidth() + 2 * DB_LABEL_PADDING + 6;
                final int Y = (int) r.getHeight() + 2 * DB_LABEL_PADDING + 6;
                add(new DatabaseLabel(label, X, Y, bgColor, ownFont));

            }
        }
/*
        @Override
        public void paint(Graphics graphics) {
            super.paint(graphics);
            final Graphics2D g = (Graphics2D)graphics;
            if (candidate!=null) {
                final ArrayList<String> dbNames = new ArrayList<>(candidate.compound.databases.keySet());
                Collections.sort(dbNames);
                g.setFont(ownFont);
                final FontMetrics fm = g.getFontMetrics();
                int x=0, y=0;
                for (String dbname : dbNames) {
                    final Rectangle2D r = fm.getStringBounds(dbname, g);
                    final int w = (int)r.getWidth();
                    final int h = (int)r.getHeight();
                    g.setColor(new Color(43,94,139));
                    g.fillRoundRect(x,y, w+4, h+4, w, h);
                    g.setColor(Color.WHITE);
                    g.drawString(dbname, x+2, h-2);
                    x += w + 5;

                }
            }
        }
        */
    }

    private static class DatabaseLabel extends JPanel {
        private String name;
        private Color color;
        private de.unijena.bioinf.sirius.gui.fingerid.DatabaseLabel label;

        public DatabaseLabel(de.unijena.bioinf.sirius.gui.fingerid.DatabaseLabel label, int width, int height, Color color, Font font) {
            this.name = label.name;
            this.color = color;
            setFont(font);
            setOpaque(false);
            setPreferredSize(new Dimension(width, height));
            this.label = label;
        }

        @Override
        public void paint(Graphics graphics) {
            super.paint(graphics);
            final Graphics2D g = (Graphics2D) graphics;
            final FontMetrics m = getFontMetrics(getFont());
            final int tw = m.stringWidth(name);
            final int th = m.getHeight();
            final int w = tw + DB_LABEL_PADDING;
            final int h = th + DB_LABEL_PADDING;
            Rectangle ggp = getParent().getParent().getParent().getBounds();
            Rectangle gp = getParent().getParent().getBounds();
            Rectangle p = getParent().getBounds();
            Rectangle s = getBounds();
            final int rx = (int)(s.getX() + p.getX()+gp.getX()+ggp.getX());
            final int ry = (int)(s.getY() + p.getY()+gp.getY()+ggp.getY());

            label.rect.setBounds(rx,ry,w,h);
            g.setColor(color);
            g.fillRoundRect(2, 2, w, h, 4, 4);
            g.setColor(Color.BLACK);
            g.drawRoundRect(2, 2, w, h, 4, 4);
            g.setColor(Color.WHITE);
            g.drawString(name, 2 + (w - tw) / 2, h - (h - th) / 2);
        }
    }

    public class XLogPLabel extends JPanel {

        private double logP;
        private final DecimalFormat format = new DecimalFormat("#0.000");
        private Font font;

        public XLogPLabel() {
            this.logP = Double.NaN;
            setPreferredSize(new Dimension(128, 20));
            Map<TextAttribute, Object> map = new HashMap<TextAttribute, Object>();
            map.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
            map.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
            font = nameFont.deriveFont(map);
        }

        @Override
        public void paint(Graphics g) {
            ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            if (Double.isNaN(logP)) return;
            g.setFont(font);
            int widthB = g.getFontMetrics().stringWidth("XLogP: ");
            g.drawString("XLogP:", 0, 14);
            g.setFont(nameFont);
            g.drawString(format.format(logP), widthB, 14);
        }

        public void setLogP(double logP) {
            this.logP = logP;
            repaint();
        }
    }

    public class DescriptionPanel extends JPanel {

        protected JLabel inchi, agreements, violations;
        protected XLogPLabel xlogP;
        protected FingerprintView ag, vio;
        protected JPanel agpanel, viopanel;
        protected DatabasePanel databasePanel;

        public DescriptionPanel() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(5, 2, 2, 2));
            final JPanel namePanel = new JPanel(new BorderLayout());
            inchi = new JLabel("", SwingConstants.LEFT);
            inchi.setFont(nameFont);
            agpanel = new JPanel();
            agpanel.setOpaque(false);
            agpanel.setLayout(new BoxLayout(agpanel, BoxLayout.Y_AXIS));
            agpanel.setBorder(new EmptyBorder(5, 0, 0, 2));
            viopanel = new JPanel();
            viopanel.setOpaque(false);
            viopanel.setBorder(new EmptyBorder(5, 0, 0, 2));
            viopanel.setLayout(new BoxLayout(viopanel, BoxLayout.Y_AXIS));
            agreements = new JLabel("True Positive Predictions:", SwingConstants.LEFT);
            Map<TextAttribute, Object> map = new HashMap<TextAttribute, Object>();
            map.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
            map.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
            violations = new JLabel("False Negative Predictions:", SwingConstants.LEFT);
            agreements.setFont(nameFont.deriveFont(map));
            violations.setFont(nameFont.deriveFont(map));
            xlogP = new XLogPLabel();
            namePanel.setOpaque(false);
            namePanel.add(inchi, BorderLayout.WEST);
            namePanel.add(xlogP, BorderLayout.EAST);
            add(namePanel);
            ag = new FingerprintView(70, Color.GREEN);
            vio = new FingerprintView(50, Color.RED);
            agpanel.add(agreements);
            agpanel.add(ag);
            viopanel.add(violations);
            viopanel.add(vio);
            add(agpanel);
            add(viopanel);
            Box db = Box.createVerticalBox();
            db.setOpaque(false);
            final Box b1 = Box.createHorizontalBox();
            final JLabel dbl = new JLabel("Databases");
            dbl.setFont(nameFont.deriveFont(map));
            b1.add(Box.createHorizontalStrut(18));
            b1.add(dbl);
            b1.add(Box.createHorizontalGlue());
            db.add(b1);

            final Box b2 = Box.createHorizontalBox();
            databasePanel = new DatabasePanel();
            b2.add(databasePanel);
            db.add(b2);
            add(db);
            setVisible(true);
        }

        public void setCompound(CompoundCandidate value) {
            setFont(propertyFont);
            inchi.setText(value.compound.inchi.key2D());
            databasePanel.setCompound(value);
            xlogP.setLogP(value.compound.xlogP);
            if (data == null) {
                ag.agreement = null;
                vio.agreement = null;
            } else {
                ag.setAgreement(value.getAgreement(computation, data.platts));
                vio.setAgreement(value.getMissings(computation, data.platts));
            }
        }
    }

}
