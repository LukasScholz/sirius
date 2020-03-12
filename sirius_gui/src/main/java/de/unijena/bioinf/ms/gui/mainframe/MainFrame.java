package de.unijena.bioinf.ms.gui.mainframe;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.swing.DefaultEventSelectionModel;
import de.unijena.bioinf.ms.frontend.core.ApplicationCore;
import de.unijena.bioinf.ms.frontend.io.projectspace.GuiProjectSpaceManager;
import de.unijena.bioinf.ms.frontend.io.projectspace.InstanceBean;
import de.unijena.bioinf.ms.frontend.io.projectspace.ProjectSpaceManager;
import de.unijena.bioinf.ms.frontend.subtools.InputFilesOptions;
import de.unijena.bioinf.ms.gui.compute.JobDialog;
import de.unijena.bioinf.ms.gui.compute.jjobs.Jobs;
import de.unijena.bioinf.ms.gui.dialogs.QuestionDialog;
import de.unijena.bioinf.ms.gui.dialogs.input.DragAndDrop;
import de.unijena.bioinf.ms.gui.io.LoadController;
import de.unijena.bioinf.ms.gui.io.spectrum.csv.CSVFormatReader;
import de.unijena.bioinf.ms.gui.mainframe.instance_panel.CompoundList;
import de.unijena.bioinf.ms.gui.mainframe.instance_panel.ExperimentListView;
import de.unijena.bioinf.ms.gui.mainframe.instance_panel.FilterableExperimentListPanel;
import de.unijena.bioinf.ms.gui.mainframe.result_panel.ResultPanel;
import de.unijena.bioinf.ms.gui.molecular_formular.FormulaList;
import de.unijena.bioinf.ms.gui.net.ConnectionMonitor;
import de.unijena.bioinf.projectspace.ProjectSpaceEvent;
import de.unijena.bioinf.projectspace.ProjectSpaceIO;
import de.unijena.bioinf.projectspace.SiriusProjectSpace;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MainFrame extends JFrame implements DropTargetListener {

    public static final MainFrame MF = new MainFrame();

    // Project Space
    private GuiProjectSpaceManager ps;

    public GuiProjectSpaceManager ps() {
        return ps;
    }

    //left side panel
    private CompoundList compoundList;

    public CompoundList getCompoundList() {
        return compoundList;
    }

    public EventList<InstanceBean> getCompounds() {
        return compoundList.getCompoundList();
    }

    public DefaultEventSelectionModel<InstanceBean> getCompoundListSelectionModel() {
        return compoundList.getCompoundListSelectionModel();
    }


    // right side panel
    private FormulaList formulaList;

    public FormulaList getFormulaList() {
        return formulaList;
    }

    private ResultPanel resultsPanel;

    public ResultPanel getResultsPanel() {
        return resultsPanel;
    }


    //job dialog
    private JobDialog jobDialog;

    public JobDialog getJobDialog() {
        return jobDialog;
    }

    //toolbar
    private SiriusToolbar toolbar;

    public SiriusToolbar getToolbar() {
        return toolbar;
    }


    //drop target for file input
    private DropTarget dropTarget;


    //internet connection monitor
    public static final ConnectionMonitor CONNECTION_MONITOR = new ConnectionMonitor();


    // some global switch that should better be within the property manager
    private boolean fingerid;

    public void setFingerIDEnabled(boolean enableFingerID) {
        fingerid = enableFingerID;
    }

    public boolean isFingerid() {
        return fingerid;
    }



    // methods for creating the mainframe
    private MainFrame() {
        super(ApplicationCore.VERSION_STRING());
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, this); //todo do we want to have the left table as drop target?
    }

    public void setTitlePath(String path) {
        setTitle(ApplicationCore.VERSION_STRING() + " on Project: '" + path + "'");
    }


    public void openNewProjectSpace(Path selFile) {
        final BasicEventList<InstanceBean> psList = this.ps.COMPOUNT_LIST;
        this.ps = Jobs.runInBackgroundAndLoad(MF, "Opening new Project...", () -> {
            SiriusProjectSpace ps = new ProjectSpaceIO(ProjectSpaceManager.newDefaultConfig()).openExistingProjectSpace(selFile);
            //todo we need to cancel all running computations here.
            System.out.println("todo we need to cancel all running computations here!");
            final GuiProjectSpaceManager gps = new GuiProjectSpaceManager(ps, psList);
            inEDTAndWait(() -> MF.setTitlePath(gps.projectSpace().getLocation().toString()));
            gps.projectSpace().addProjectSpaceListener(event -> {
                if (event.equals(ProjectSpaceEvent.LOCATION_CHANGED))
                    inEDTAndWait(() -> MF.setTitlePath(gps.projectSpace().getLocation().toString()));
            });
            return gps;
        }).getResult();
    }

    public void decoradeMainFrameInstance(@NotNull GuiProjectSpaceManager projectSpaceManager) {
        //create computation
        //todo get predictor from application core?
        // create project space
        ps = projectSpaceManager;
        inEDTAndWait(() -> MF.setTitlePath(ps.projectSpace().getLocation().toString()));


        // create models for views
        compoundList = new CompoundList(ps);
        formulaList = new FormulaList(compoundList);


        //CREATE VIEWS
        jobDialog = new JobDialog(this);
        // results Panel
        resultsPanel = new ResultPanel(formulaList, ApplicationCore.WEB_API);

        toolbar = new SiriusToolbar();

        final JPanel mainPanel = new JPanel(new BorderLayout());

        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 1, 5, 1));
        add(mainPanel, BorderLayout.CENTER);

        //build left sidepane
        FilterableExperimentListPanel experimentListPanel = new FilterableExperimentListPanel(new ExperimentListView(compoundList));

        //BUILD the MainFrame (GUI)
        final JTabbedPane tabbedPane = new JTabbedPane(SwingConstants.TOP, JTabbedPane.WRAP_TAB_LAYOUT);
        tabbedPane.addTab("Compounds", experimentListPanel);
        tabbedPane.addTab("Identifications", new JPanel());
        tabbedPane.setEnabledAt(1, false);
        tabbedPane.setPreferredSize(new Dimension(218, (int) tabbedPane.getPreferredSize().getHeight()));
        mainPanel.add(tabbedPane, BorderLayout.WEST);
        mainPanel.add(resultsPanel, BorderLayout.CENTER);
        add(toolbar, BorderLayout.NORTH);

        setSize(new Dimension(1368, 1024));
        setVisible(true);

    }

    @Override
    public void dispose() {
        resultsPanel.dispose();
        super.dispose();
    }


    //////////////////////////////////////////////////
    ////////////////// drag and drop /////////////////
    //////////////////////////////////////////////////

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
        // TODO Auto-generated method stub

    }

    @Override
    public void dragOver(DropTargetDragEvent dtde) {
        // TODO Auto-generated method stub

    }

    @Override
    public void dropActionChanged(DropTargetDragEvent dtde) {
        // TODO Auto-generated method stub

    }

    @Override
    public void dragExit(DropTargetEvent dte) {
        // TODO Auto-generated method stub

    }

    public static final String DONT_ASK_OPEN_KEY = "de.unijena.bioinf.sirius.dragdrop.open.dontAskAgain";

    @Override
    public void drop(DropTargetDropEvent dtde) {
        final List<File> newFiles = resolveFileList(DragAndDrop.getFileListFromDrop(dtde));
        boolean openNewProject = false;

        if (newFiles.size() > 0) {
            if (newFiles.size() == 1 && (ProjectSpaceIO.isExistingProjectspaceDirectory(newFiles.get(0).toPath()) || ProjectSpaceIO.isZipProjectSpace(newFiles.get(0).toPath())))
                openNewProject = new QuestionDialog(MF, "<html><body>Do you want to open the dropped Project instead of importing it? <br> The currently opened project will be closed!</br></body></html>"/*, DONT_ASK_OPEN_KEY*/).isSuccess();

            if (openNewProject)
                MF.openNewProjectSpace(newFiles.get(0).toPath());
            else
                importDragAndDropFiles(newFiles);

        }
    }


    private void importDragAndDropFiles(List<File> rawFiles) {
        final InputFilesOptions input = ps.importOneExperimentPerLocation(rawFiles); //import all batch mode importable file types (e.g. .sirius, project-dir, .ms, .mgf, .mzml, .mzxml)

        // check if unknown files contain csv files with spectra
        final CSVFormatReader csvChecker = new CSVFormatReader();
        List<File> csvFiles = input.msInput != null ? input.msInput.unknownFiles.stream().map(Path::toFile)
                .filter(f -> csvChecker.isCompatible(f) || f.getName().toLowerCase().endsWith(".txt"))
                .collect(Collectors.toList()) : Collections.emptyList();

        if (!csvFiles.isEmpty())
            openImporterWindow(csvFiles, Collections.emptyList(), Collections.emptyList());
    }

    private void openImporterWindow(List<File> csvFiles, List<File> msFiles, List<File> mgfFiles) {
        LoadController lc = new LoadController(this);
        lc.addSpectra(csvFiles, msFiles, mgfFiles);
        lc.showDialog();
    }

    public static List<File> resolveFileList(List<File> files) {
        final ArrayList<File> filelist = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() && !ProjectSpaceIO.isExistingProjectspaceDirectory(f.toPath())) {
                    final File[] fl = f.listFiles();
                    if (fl != null) {
                        for (File g : fl)
                            if (!g.isDirectory()) filelist.add(g);
                    }
                } else {
                    filelist.add(f);
                }
            }
        }
        return filelist;
    }

    public static void inEDTAndWait(@NotNull final Runnable run) {
        if (SwingUtilities.isEventDispatchThread()) {
            run.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(run);
            } catch (InterruptedException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

}


