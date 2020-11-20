/*
 *  This file is part of the SIRIUS Software for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2020 Kai Dührkop, Markus Fleischauer, Marcus Ludwig, Martin A. Hoffman, Fleming Kretschmer, Marvin Meusel and Sebastian Böcker,
 *  Chair of Bioinformatics, Friedrich-Schilller University.
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Affero General Public License
 *  as published by the Free Software Foundation; either
 *  version 3 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>
 */

package de.unijena.bioinf.ms.frontend.subtools.projectspace;

import de.unijena.bioinf.ChemistryBase.ms.Ms2Experiment;
import de.unijena.bioinf.ChemistryBase.utils.FileUtils;
import de.unijena.bioinf.fingerid.ConfidenceScore;
import de.unijena.bioinf.fingerid.blast.FBCandidates;
import de.unijena.bioinf.fingerid.blast.TopCSIScore;
import de.unijena.bioinf.jjobs.Partition;
import de.unijena.bioinf.ms.annotations.WriteSummaries;
import de.unijena.bioinf.ms.frontend.subtools.InputFilesOptions;
import de.unijena.bioinf.ms.frontend.subtools.RootOptions;
import de.unijena.bioinf.ms.frontend.workflow.Workflow;
import de.unijena.bioinf.ms.properties.ParameterConfig;
import de.unijena.bioinf.projectspace.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ProjectSpaceWorkflow implements Workflow {


    private final RootOptions<?, ?, ?> rootOptions;
    private final ProjecSpaceOptions projecSpaceOptions;
    private final ParameterConfig config;

    public ProjectSpaceWorkflow(RootOptions<?, ?, ?> rootOptions, ProjecSpaceOptions projecSpaceOptions, ParameterConfig config) {
        this.rootOptions = rootOptions;
        this.projecSpaceOptions = projecSpaceOptions;
        this.config = config;
    }

    @Override
    public void run() {
        final Predicate<CompoundContainerId> cidFilter = projecSpaceOptions.getCombinedCIDFilter();
        final Predicate<Ms2Experiment> expFilter = projecSpaceOptions.getCombinedMS2ExpFilter();
        final ProjecSpaceOptions.SplitProject splitOpts = projecSpaceOptions.splitOptions;
        boolean move = projecSpaceOptions.move;

        try {
            if (!splitOpts.type.equals(ProjecSpaceOptions.SplitProject.SplitType.NO) && splitOpts.count > 1) {
//                LoggerFactory.getLogger(getClass()).info("The Splitting tool works only on project-spaces. Other inputs will be ignored!");
                final InputFilesOptions projectInput = rootOptions.getInput();

                ProjectSpaceManager source = null;
                try {
                    if (projectInput.msInput.projects.size() > 1 || !projectInput.msInput.msParserfiles.isEmpty() || (projectInput.csvInputs != null && !projectInput.csvInputs.isEmpty())) {
                        source = rootOptions.getSpaceManagerFactory().create(
                                new ProjectSpaceIO(ProjectSpaceManager.newDefaultConfig()).createTemporaryProjectSpace(),
                                rootOptions.getOutput().getProjectSpaceFilenameFormatter());
                        InstanceImporter importer = new InstanceImporter(source, expFilter, cidFilter, projecSpaceOptions.move, rootOptions.getOutput().isUpdateFingerprints());
                        importer.doImport(projectInput);
                        move = true;
                    } else if (projectInput.msInput.projects.size() == 1) {
                        source = rootOptions.getSpaceManagerFactory().create(
                                new ProjectSpaceIO(ProjectSpaceManager.newDefaultConfig()).openExistingProjectSpace(projectInput.msInput.projects.get(0)));
                    } else {
                        LoggerFactory.getLogger(getClass()).warn("No input project-space given! Nothing to do");
                        return;
                    }


                    List<CompoundContainerId> cids = new ArrayList<>(source.size());
                    source.projectSpace().filteredIterator(cidFilter).forEachRemaining(cids::add);

                    // do io intense filtering
                    @Nullable Predicate<Instance> instFilter = projecSpaceOptions.getCombinedInstanceilter();
                    if (instFilter != null) {
                        final ProjectSpaceManager finalSource = source;
                        cids.removeIf(id -> instFilter.test(finalSource.newInstanceFromCompound(id)));
                    }

                    switch (splitOpts.order) {
                        case SHUFFLE:
                            Collections.shuffle(cids);
                            break;
                        case MASS:
                            cids.sort(Comparator.comparing(c -> c.getIonMass().orElse(Double.NaN)));
                            break;
                        case NAME:
                            cids.sort(Comparator.comparing(CompoundContainerId::getDirectoryName));
                            break;
                    }

                    final Partition<CompoundContainerId> part = splitOpts.type.equals(ProjecSpaceOptions.SplitProject.SplitType.NUMBER)
                            ? Partition.ofNumber(cids, splitOpts.count) : Partition.ofSize(cids, splitOpts.count);

                    LoggerFactory.getLogger(getClass()).info("Writing '" + part.size() + "' batches of size: " + part.get(0).size());

                    //get file and and preserve extension for compression
                    final Path outputLocation = rootOptions.getOutput().getOutputProjectLocation();
                    final String fileName = outputLocation.getFileName().toString();
                    final Path parent = Files.isDirectory(outputLocation) ? outputLocation : outputLocation.getParent();
                    int idx = fileName.lastIndexOf('.');

                    final String name = idx < 0 ? fileName : fileName.substring(0,idx);
                    final String ext = idx < 0 ? "" : fileName.substring(idx);
                    for (int i = 0; i < part.size(); i++) {
                        final Set<CompoundContainerId> p = new HashSet<>(part.get(i));
                        ProjectSpaceManager batchSpace = null;
                        try {
                            batchSpace = rootOptions.getSpaceManagerFactory().create(
                                    new ProjectSpaceIO(ProjectSpaceManager.newDefaultConfig()).createNewProjectSpace(parent.resolve(name + "_" + i + ext)),
                                    source.nameFormatter);

                            LoggerFactory.getLogger(getClass()).info("Copying compounds '" + p.stream().map(CompoundContainerId::getDirectoryName).collect(Collectors.joining(",")) + "' to Batch '" + batchSpace.projectSpace().getLocation().toString());
                            InstanceImporter.importProject(source.projectSpace(), batchSpace, expFilter, (cid) -> p.contains(cid) && cidFilter.test(cid), move, rootOptions.getOutput().isUpdateFingerprints());

                            if (config.createInstanceWithDefaults(WriteSummaries.class).value) {
                                LoggerFactory.getLogger(getClass()).info("(Re)Writing Summaries of Batch '" + batchSpace.projectSpace().getLocation().toString());
                                batchSpace.updateSummaries(ProjectSpaceManager.defaultSummarizer());
                            }
                        } finally {
                            if (batchSpace != null)
                                batchSpace.close();
                        }
                        LoggerFactory.getLogger(getClass()).info("Batch '" + batchSpace.projectSpace().getLocation().toString() + "' successfully written!");
                    }

                    source.close();
                    if (move)
                        FileUtils.deleteRecursively(source.projectSpace().getLocation());
                } catch (IOException | ExecutionException e) {
                    LoggerFactory.getLogger(getClass()).error("Error when filtering and splitting Project(s)!",e);
                } finally {
                    if (source != null)
                        source.close();
                }
            } else {
                final ProjectSpaceManager space = rootOptions.getProjectSpace();
                try {
                    InputFilesOptions input = rootOptions.getInput();

                    // if the output project is also part of the input, we have to filter it also
                    if (space.size() > 0 && input == null || input.msInput.projects.contains(space.projectSpace().getLocation())) {
                        space.projectSpace().filteredIterator(c -> !cidFilter.test(c)).forEachRemaining(id -> {
                            try {
                                space.projectSpace().deleteCompound(id);
                                LoggerFactory.getLogger(getClass()).error("Deleting: " + id.getDirectoryName());
                            } catch (IOException e) {
                                LoggerFactory.getLogger(getClass()).error("Could not delete Instance with ID: " + id.getDirectoryName());
                            }
                        });
                    }


                    new InstanceImporter(space, expFilter, cidFilter, projecSpaceOptions.move, rootOptions.getOutput().isUpdateFingerprints())
                            .doImport(input);


                    if (projecSpaceOptions.repairScores)
                        space.forEach(instance -> {
                            instance.loadFormulaResults(FormulaScoring.class, FBCandidates.class).forEach(res -> {
                                if (res.getCandidate().getAnnotation(FormulaScoring.class).map(s -> (s.hasAnnotation(TopCSIScore.class) || s.hasAnnotation(ConfidenceScore.class))).orElse(false)) {
                                    if (!res.getCandidate().hasAnnotation(FBCandidates.class)) {
                                        LoggerFactory.getLogger(getClass()).info("Repairing score file of: " + res.getCandidate().getId());
                                        res.getCandidate().getAnnotationOrThrow(FormulaScoring.class).removeAnnotation(TopCSIScore.class);
                                        res.getCandidate().getAnnotationOrThrow(FormulaScoring.class).removeAnnotation(ConfidenceScore.class);
                                        instance.updateFormulaResult(res.getCandidate(), FormulaScoring.class);
                                    }
                                }
                            });
                        });

                    // io intense filters are applied as last
                    filterOnInstanceLevel(space, projecSpaceOptions);


                    if (config.createInstanceWithDefaults(WriteSummaries.class).value) {
                        LoggerFactory.getLogger(getClass()).info("(Re)Writing Summaries of '" + space.projectSpace().getLocation().toString());
                        space.updateSummaries(ProjectSpaceManager.defaultSummarizer());
                    }
                } catch (ExecutionException e) {
                    LoggerFactory.getLogger(getClass()).error("Error when filtering Project(s)!", e);
                } finally {
                    space.close();
                }
            }
        } catch (IOException e) {
            LoggerFactory.getLogger(getClass()).error("Error when closing Project(s)!", e);
        }
    }

    private void filterOnInstanceLevel(ProjectSpaceManager outputProject, ProjecSpaceOptions projecSpaceOptions) {
        final Predicate<Instance> pred = projecSpaceOptions.getCombinedInstanceilter();
        if (pred == null)
            return;

        LoggerFactory.getLogger(getClass()).info("Filtering with IO intense instance filters... '" + outputProject.projectSpace().getLocation().toString());

        List<CompoundContainerId> cidsToDelete = new ArrayList<>();
        outputProject.iterator().forEachRemaining(inst -> {
            if (!pred.test(inst))
                cidsToDelete.add(inst.getID());
        });

        for (CompoundContainerId id : cidsToDelete) {
            try {
                outputProject.projectSpace().deleteCompound(id);
                LoggerFactory.getLogger(getClass()).error("Deleting (InstanceFilter): " + id.getDirectoryName());
            } catch (IOException e) {
                LoggerFactory.getLogger(getClass()).error("Could not delete Instance with ID: " + id.getDirectoryName());
            }
        }
    }
}
