/*
 *  This file is part of the SIRIUS Software for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2020 Kai Dührkop, Markus Fleischauer, Marcus Ludwig, Martin A. Hoffman, Fleming Kretschmer, Marvin Meusel and Sebastian Böcker,
 *  Chair of Bioinformatics, Friedrich-Schiller University.
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Affero General Public License
 *  as published by the Free Software Foundation; either
 *  version 3 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License along with SIRIUS.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>
 */

package de.unijena.bioinf.projectspace;

import ca.odell.glazedlists.BasicEventList;
import de.unijena.bioinf.ChemistryBase.ms.Ms2Experiment;
import de.unijena.bioinf.jjobs.TinyBackgroundJJob;
import de.unijena.bioinf.ms.frontend.subtools.InputFilesOptions;
import de.unijena.bioinf.ms.frontend.subtools.canopus.CanopusOptions;
import de.unijena.bioinf.ms.frontend.subtools.fingerblast.FingerblastOptions;
import de.unijena.bioinf.ms.frontend.subtools.fingerprint.FingerprintOptions;
import de.unijena.bioinf.ms.frontend.subtools.lcms_align.LcmsAlignOptions;
import de.unijena.bioinf.ms.frontend.subtools.lcms_align.LcmsAlignSubToolJob;
import de.unijena.bioinf.ms.gui.compute.jjobs.Jobs;
import de.unijena.bioinf.ms.gui.dialogs.ExceptionDialog;
import de.unijena.bioinf.ms.gui.dialogs.QuestionDialog;
import de.unijena.bioinf.ms.gui.mainframe.MainFrame;
import de.unijena.bioinf.ms.gui.table.SiriusGlazedLists;
import de.unijena.bioinf.ms.gui.utils.GuiUtils;
import de.unijena.bioinf.projectspace.canopus.CanopusDataProperty;
import de.unijena.bioinf.projectspace.fingerid.FingerIdDataProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static de.unijena.bioinf.ms.gui.mainframe.MainFrame.MF;
import static de.unijena.bioinf.ms.gui.mainframe.MainFrame.inEDTAndWait;

public class GuiProjectSpaceManager extends ProjectSpaceManager {
    protected static final Logger LOG = LoggerFactory.getLogger(GuiProjectSpaceManager.class);
    public final BasicEventList<InstanceBean> INSTANCE_LIST;


    protected final InstanceBuffer ringBuffer;
    private ContainerListener.Defined createListener;
    private ContainerListener.Defined computeListener;


    public GuiProjectSpaceManager(@NotNull SiriusProjectSpace space, int maxBufferSize) {
        this(space, new BasicEventList<>(), maxBufferSize);
    }

    public GuiProjectSpaceManager(@NotNull SiriusProjectSpace space, BasicEventList<InstanceBean> compoundList, int maxBufferSize) {
        this(space, compoundList, null, maxBufferSize);
    }

    public GuiProjectSpaceManager(@NotNull SiriusProjectSpace space, BasicEventList<InstanceBean> compoundList, @Nullable Function<Ms2Experiment, String> formatter, int maxBufferSize) {
        super(space, new InstanceBeanFactory(), formatter);
        this.ringBuffer = new InstanceBuffer(maxBufferSize);
        this.INSTANCE_LIST = compoundList;
        final ArrayList<InstanceBean> buf = new ArrayList<>(size());
        forEach(it -> {
            it.clearFormulaResultsCache();
            it.clearCompoundCache();
            buf.add((InstanceBean) it);
        });
        inEDTAndWait(() -> {
            INSTANCE_LIST.clear();
            INSTANCE_LIST.addAll(buf);
        });

        createListener = projectSpace().defineCompoundListener().onCreate().thenDo((event -> {
            final InstanceBean inst = (InstanceBean) newInstanceFromCompound(event.getAffectedID());
            Jobs.runEDTLater(() -> INSTANCE_LIST.add(inst));
        })).register();

        computeListener = projectSpace().defineCompoundListener().on(ContainerEvent.EventType.ID_FLAG).thenDo(event -> {
            if (event.getAffectedIDs().isEmpty() || !event.getAffectedIdFlags().contains(CompoundContainerId.Flag.COMPUTING))
                return;

            Set<CompoundContainerId> eff = new HashSet<>(event.getAffectedIDs());
            //todo do we want an index of ID to InstanceBean
            Set<InstanceBean> upt = INSTANCE_LIST.stream().filter(i -> eff.contains(i.getID())).collect(Collectors.toSet());
            Jobs.runEDTLater(() -> SiriusGlazedLists.multiUpdate(MainFrame.MF.getCompoundList().getCompoundList(), upt));
        }).register();
    }


    public <E extends ProjectSpaceProperty> Optional<E> loadProjectSpaceProperty(Class<E> propertyKey) {
        return projectSpace().getProjectSpaceProperty(propertyKey);
    }


    public synchronized void deleteCompounds(@Nullable final List<InstanceBean> insts) {
        if (insts == null || insts.isEmpty())
            return;
        Jobs.runInBackgroundAndLoad(MF, "Deleting Compounds...", false, new TinyBackgroundJJob<Boolean>() {
            @Override
            protected Boolean compute() throws Exception {
                final AtomicInteger pro = new AtomicInteger(0);
                updateProgress(0, insts.size(), pro.get(), "Deleting...");
                ringBuffer.removeAllLazy(insts);
                inEDTAndWait(() -> INSTANCE_LIST.removeAll(insts));
                insts.iterator().forEachRemaining(inst -> {
                    try {
                        projectSpace().deleteCompound(inst.getID());
                    } catch (IOException e) {
                        LOG.error("Could not delete Compound: " + inst.getID(), e);
                    } finally {
                        updateProgress(0, insts.size(), pro.incrementAndGet(), "Deleting...");
                    }
                });
                return true;
            }
        });

    }

    public synchronized void deleteAll() {
        deleteCompounds(INSTANCE_LIST);
    }


    public synchronized void importOneExperimentPerLocation(@NotNull final List<File> inputFiles) {
        final InputFilesOptions inputF = new InputFilesOptions();
        inputF.msInput = Jobs.runInBackgroundAndLoad(MF, "Analyzing Files...", false,
                InstanceImporter.makeExpandFilesJJob(inputFiles)).getResult();
        importOneExperimentPerLocation(inputF);
    }

    public synchronized void importOneExperimentPerLocation(@NotNull final InputFilesOptions input) {
        boolean align = Jobs.runInBackgroundAndLoad(MF, "Checking for alignable input...", () ->
                (input.msInput.msParserfiles.size() > 1 && input.msInput.projects.size() == 0 && input.msInput.msParserfiles.keySet().stream().map(p -> p.getFileName().toString().toLowerCase()).allMatch(n -> n.endsWith(".mzml") || n.endsWith(".mzxml"))))
                .getResult();

        // todo this is hacky we need some real view for that at some stage.
        if (align)
            align = new QuestionDialog(MF, "<html><body> You inserted multiple LC-MS/MS Runs. <br> Do you want to Align them during import?</br></body></html>"/*, DONT_ASK_OPEN_KEY*/).isSuccess();
        try {
            createListener.unregister();
            if (align) {
                //todo would be nice to update all at once!
                final LcmsAlignSubToolJob j = new LcmsAlignSubToolJob(input, this, null, new LcmsAlignOptions());
                Jobs.runInBackgroundAndLoad(MF, j);
                INSTANCE_LIST.addAll(j.getImportedCompounds().stream()
                        .map(id -> (InstanceBean) newInstanceFromCompound(id))
                        .collect(Collectors.toList()));
            } else {
                final List<Path> outdated = Jobs.runInBackgroundAndLoad(MF, "Checking for incompatible data...", new TinyBackgroundJJob<List<Path>>() {
                    @Override
                    protected List<Path> compute() throws Exception {
                        if (input.msInput.projects.size() == 0)
                            return List.of();
                        final List<Path> out = new ArrayList<>(input.msInput.projects.size());
                        for(Path p : input.msInput.projects.keySet()){
                            if (InstanceImporter.checkDataCompatibility(p,GuiProjectSpaceManager.this,this::checkForInterruption) != null)
                                out.add(p);
                        }
                        return out;
                    }
                }).getResult();

                boolean updateIfNeeded = !outdated.isEmpty() && new QuestionDialog(MF, GuiUtils.formatToolTip(
                        "The following input projects are incompatible with the target", "'" + this.projectSpace().getLocation()+ "'", "",
                        outdated.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(",")),"",
                        "Do you wish to import and update the fingerprint data?","WARNING: All fingerprint related results will be excluded during import (CSI:FingerID, CANOPUS)")).isSuccess();

                InstanceImporter importer = new InstanceImporter(this,
                        x -> {
                            if (x.getPrecursorIonType() != null) {
                                return true;
                            } else {
                                LOG.warn("Skipping `" + x.getName() + "` because of Missing IonType! This is likely to be A empty Measurement.");
                                return false;
                            }
                        },
                        x -> true, false, updateIfNeeded
                );
                List<InstanceBean> imported = Jobs.runInBackgroundAndLoad(MF, "Auto-Importing supported Files...",  importer.makeImportJJob(input))
                        .getResult().stream().map(id -> (InstanceBean) newInstanceFromCompound(id)).collect(Collectors.toList());

                Jobs.runInBackgroundAndLoad(MF, "Showing imported data...",
                        () -> Jobs.runEDTLater(() -> INSTANCE_LIST.addAll(imported)));
            }
        } finally {
            createListener.register();
        }
    }

    protected synchronized void copy(Path newlocation, boolean switchLocation) {
        String header = switchLocation ? "Saving Project to" : "Saving a Copy to";
        final IOException ex = Jobs.runInBackgroundAndLoad(MF, header + " '" + newlocation.toString() + "'...", () -> {
            try {
                ProjectSpaceIO.copyProject(projectSpace(), newlocation, switchLocation);
                inEDTAndWait(() -> MF.setTitlePath(projectSpace().getLocation().toString()));
                return null;
            } catch (IOException e) {
                return e;
            }
        }).getResult();

        if (ex != null)
            new ExceptionDialog(MF, ex.getMessage());
    }

    public synchronized void updateFingerprintData() {
        Jobs.runInBackgroundAndLoad(MF, new TinyBackgroundJJob<Boolean>() {
            @Override
            protected Boolean compute() throws Exception {
                List<Consumer<Instance>> invalidators = new ArrayList<>();
                invalidators.add(new FingerprintOptions(null).getInvalidator());
                invalidators.add(new FingerblastOptions(null).getInvalidator());
                invalidators.add(new CanopusOptions(null).getInvalidator());
                final AtomicInteger progress = new AtomicInteger(0);
                int max = INSTANCE_LIST.size() * invalidators.size() + 3;
                updateProgress(0, max, progress.getAndIncrement(), "Starting Update...");
                // remove fingerprint related results
                INSTANCE_LIST.forEach(i -> invalidators.forEach(inv -> {
                    updateProgress(0, max, progress.getAndIncrement(), "Deleting results for '" + i.getName() + "'...");
                    inv.accept(i);
                }));
                //remove Fingerprint data
                updateProgress(0, max, progress.getAndIncrement(), "delete CSI:FinerID Data");
                deleteProjectSpaceProperty(FingerIdDataProperty.class);
                updateProgress(0, max, progress.getAndIncrement(), "delete CANOPUS Data");
                deleteProjectSpaceProperty(CanopusDataProperty.class);
                updateProgress(0, max, progress.get(), "DONE!");
                return true;
            }
        });
    }

    /**
     * Saves (copy) the project to a new location. New location is active
     *
     * @param newlocation The path where the project will be saved
     * @throws IOException Thrown if writing of archive fails
     */
    public void saveAs(Path newlocation) {
        copy(newlocation, true);
    }

    /**
     * Saves a copy of the project to a new location. Original location will be the active project.
     *
     * @param copyLocation The path where the project will be saved
     * @throws IOException Thrown if writing of archive fails
     */
    public void saveCopy(Path copyLocation) throws IOException {
        copy(copyLocation, false);
    }

    @Override
    public void close() throws IOException {
        createListener.unregister();
        createListener = null;
        super.close();
    }

    public void setComputing(List<InstanceBean> instances, boolean computing) {
        projectSpace().setFlags(CompoundContainerId.Flag.COMPUTING, computing,
                instances.stream().distinct().map(Instance::getID).toArray(CompoundContainerId[]::new));
    }
}
