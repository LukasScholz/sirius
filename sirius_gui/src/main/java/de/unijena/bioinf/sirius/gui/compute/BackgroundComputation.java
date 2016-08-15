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

package de.unijena.bioinf.sirius.gui.compute;

import de.unijena.bioinf.ChemistryBase.chem.FormulaConstraints;
import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;
import de.unijena.bioinf.ChemistryBase.chem.PrecursorIonType;
import de.unijena.bioinf.ChemistryBase.ms.Deviation;
import de.unijena.bioinf.ChemistryBase.ms.Ms2Experiment;
import de.unijena.bioinf.FragmentationTreeConstruction.computation.tree.ilp.GurobiSolver;
import de.unijena.bioinf.chemdb.BioFilter;
import de.unijena.bioinf.chemdb.FormulaCandidate;
import de.unijena.bioinf.chemdb.RESTDatabase;
import de.unijena.bioinf.sirius.*;
import de.unijena.bioinf.sirius.gui.fingerid.WebAPI;
import de.unijena.bioinf.sirius.gui.io.SiriusDataConverter;
import de.unijena.bioinf.sirius.gui.mainframe.MainFrame;
import de.unijena.bioinf.sirius.gui.structure.ComputingStatus;
import de.unijena.bioinf.sirius.gui.structure.ExperimentContainer;

import javax.swing.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class BackgroundComputation {


    private final MainFrame owner;
    private final ConcurrentLinkedQueue<Task> queue;
    private final Worker[] workers;
    private final int nworkers;
    private final ConcurrentLinkedQueue<ExperimentContainer> cancel;

    public BackgroundComputation(MainFrame owner) {
        this.owner = owner;
        this.queue = new ConcurrentLinkedQueue<>();
        this.cancel = new ConcurrentLinkedQueue<>();
        int nw;
        try {
        if (new Sirius().getMs2Analyzer().getTreeBuilder().getDescription().equalsIgnoreCase("Gurobi")) {
            nw = Math.max(1, Runtime.getRuntime().availableProcessors()/2);
        } else {
            nw = 1;
        }} catch (Throwable t) {
            nw = 1;
        }
        this.nworkers = nw;
        this.workers = new Worker[nw];
    }

    public void cancel(ExperimentContainer container) {
        final Iterator<Task> iter = queue.iterator();
        while (iter.hasNext()) {
            final Task t = iter.next();
            if (t.exp==container) {
                iter.remove();
                t.job.error("Canceled", null);
                break;
            }
        }
        container.setComputeState(ComputingStatus.UNCOMPUTED);
        cancel.add(container);
    }

    public List<ExperimentContainer> cancelAll() {
        final ArrayList<ExperimentContainer> canceled = new ArrayList<>();
        final Iterator<Task> iter = queue.iterator();
        while (iter.hasNext()) {
            final Task t = iter.next();
            canceled.add(t.exp);
            iter.remove();
            t.exp.setComputeState(ComputingStatus.UNCOMPUTED);
            t.job.error("Canceled", null);
        }
        synchronized (this) {
            for (Worker w : workers) {
                if (w != null && !w.isDone()) {
                    final ExperimentContainer currentComputation = w.currentComputation;
                    if (currentComputation!=null)
                        cancel(currentComputation);
                }
            }
        }
        return canceled;
    }

    public void add(Task containers) {
        this.queue.add(containers);
        containers.exp.setComputeState(ComputingStatus.QUEUED);
        wakeup();
    }

    public void addAll(Collection<Task> containers) {
        for (Task t : containers) {
            t.exp.setComputeState(ComputingStatus.QUEUED);
        }
        this.queue.addAll(containers);
        wakeup();
    }

    private void wakeup() {
        synchronized(this) {
            for (int w=0; w < workers.length; ++w) {
                final Worker worker = workers[w];
                if (worker == null || worker.isDone()) {
                    workers[w] = new Worker();
                    workers[w].execute();
                }
            }
        }
    }

    public final static class Task {
        private final ExperimentContainer exp;
        private final FormulaConstraints constraints;
        private final double ppm;
        private final int numberOfCandidates;
        private final String profile;
        private final JobLog.Job job;

        private final FormulaSource formulaSource;

        private volatile List<IdentificationResult> results;
        private volatile ComputingStatus state;

        public Task(String profile, ExperimentContainer exp, FormulaConstraints constraints, double ppm, int numberOfCandidates, FormulaSource formulaSource) {
            this.profile = profile;
            this.exp = exp;
            this.constraints = constraints;
            this.ppm = ppm;
            this.numberOfCandidates = numberOfCandidates;
            this.formulaSource = formulaSource;
            this.state = exp.getComputeState();
            this.results = exp.getRawResults();
            this.job = JobLog.getInstance().submit(exp.getGUIName(), "compute trees");
        }
    }

    private class Worker extends SwingWorker<List<ExperimentContainer>, Task> {

        protected final HashMap<String, Sirius> siriusPerProfile = new HashMap<>();
        protected volatile ExperimentContainer currentComputation;

        @Override
        protected void process(List<Task> chunks) {
            super.process(chunks);
            for (Task c : chunks) {
                c.exp.setComputeState(c.state);
                if (c.state==ComputingStatus.COMPUTED || c.state==ComputingStatus.FAILED) {
                    c.exp.setRawResults(c.results);
                    c.exp.setComputeState(c.state);
                } else if (c.state == ComputingStatus.COMPUTING) {
                    currentComputation = c.exp;
                    c.job.run();
                }
                owner.refreshCompound(c.exp);
            }
        }

        private void checkProfile(Task t) {
            if (siriusPerProfile.containsKey(t.profile)) return;
            else try {
                siriusPerProfile.put(t.profile, new Sirius(t.profile));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void done() {
            owner.computationComplete();
        }

        @Override
        protected List<ExperimentContainer> doInBackground() throws Exception {
            while (!queue.isEmpty()) {
                final Task task = queue.poll();
                task.state = ComputingStatus.COMPUTING;
                task.job.run();
                publish(task);
                compute(task);
                if (task.state == ComputingStatus.COMPUTING) {
                    task.state = ComputingStatus.COMPUTED;
                    task.job.done();
                }
                publish(task);
            }
            return null;
        }

        protected void compute(final Task container) {
            checkProfile(container);
            final Sirius sirius = siriusPerProfile.get(container.profile);
            final FormulaSource formulaSource = container.formulaSource;
            sirius.setProgress(new Progress() {
                @Override
                public void init(double maxProgress) {

                }

                @Override
                public void update(double currentProgress, double maxProgress, String value, Feedback feedback) {
                    // check if task is canceled
                    final Iterator<ExperimentContainer> iter = cancel.iterator();
                    while (iter.hasNext()) {
                        final ExperimentContainer canceled = iter.next();
                        if (canceled==container.exp) {
                            feedback.cancelComputation();
                            container.state = ComputingStatus.UNCOMPUTED;
                            container.job.error("canceled",null);
                        }
                        iter.remove();
                    }
                }

                @Override
                public void finished() {

                }

                @Override
                public void info(String message) {

                }
            });
            sirius.setFormulaConstraints(container.constraints);
            sirius.getMs2Analyzer().getDefaultProfile().setAllowedMassDeviation(new Deviation(container.ppm));
            sirius.getMs1Analyzer().getDefaultProfile().setAllowedMassDeviation(new Deviation(container.ppm));

            try {
                final List<IdentificationResult> results;
                final Ms2Experiment experiment = SiriusDataConverter.experimentContainerToSiriusExperiment(container.exp);
                if (formulaSource==FormulaSource.ALL_POSSIBLE) {
                    if (experiment.getPrecursorIonType().isIonizationUnknown()) {
                        results = sirius.identifyPrecursorAndIonization(experiment,
                                container.numberOfCandidates, true, IsotopePatternHandling.score);
                    } else {
                        results = sirius.identify(experiment,
                                container.numberOfCandidates, true, IsotopePatternHandling.score);
                    }
                } else {
                    try (final RESTDatabase db = WebAPI.getRESTDb(formulaSource==FormulaSource.BIODB ? BioFilter.ONLY_BIO : BioFilter.ALL)) {
                        PrecursorIonType ionType = experiment.getPrecursorIonType();
                        PrecursorIonType[] allowedIons;
                        if (ionType.isIonizationUnknown()) {
                            allowedIons = ionType.getCharge()>0 ? WebAPI.positiveIons : WebAPI.negativeIons;
                        } else {
                            allowedIons = new PrecursorIonType[]{ionType};
                        }
                        final HashSet<MolecularFormula> formulas = new HashSet<>();
                        for (List<FormulaCandidate> fc : db.lookupMolecularFormulas(experiment.getIonMass(), new Deviation(container.ppm), allowedIons)) {
                            for (FormulaCandidate f : fc)
                                formulas.add(f.getFormula());
                        }
                        results = sirius.identify(experiment,
                                container.numberOfCandidates, true, IsotopePatternHandling.score, formulas);
                    }
                }
                container.results = results;
                if (results==null || results.size()==0) container.state = ComputingStatus.FAILED;
            } catch (Exception e) {
                e.printStackTrace();
                container.state = ComputingStatus.FAILED;
                container.job.error(e.getMessage(), e);
                container.results=new ArrayList<>();
            }
        }
    }

}
