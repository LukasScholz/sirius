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

package de.unijena.bioinf.ms.middleware.compounds;

import de.unijena.bioinf.ChemistryBase.algorithm.scoring.FormulaScore;
import de.unijena.bioinf.ChemistryBase.algorithm.scoring.SScored;
import de.unijena.bioinf.ChemistryBase.algorithm.scoring.Scored;
import de.unijena.bioinf.ChemistryBase.chem.PrecursorIonType;
import de.unijena.bioinf.ChemistryBase.ms.Ms2Experiment;
import de.unijena.bioinf.ChemistryBase.ms.Peak;
import de.unijena.bioinf.ChemistryBase.ms.Spectrum;
import de.unijena.bioinf.ChemistryBase.ms.ft.FTree;
import de.unijena.bioinf.ChemistryBase.ms.utils.Spectrums;
import de.unijena.bioinf.GibbsSampling.ZodiacScore;
import de.unijena.bioinf.babelms.CloseableIterator;
import de.unijena.bioinf.babelms.GenericParser;
import de.unijena.bioinf.babelms.MsExperimentParser;
import de.unijena.bioinf.canopus.CanopusResult;
import de.unijena.bioinf.chemdb.CompoundCandidate;
import de.unijena.bioinf.chemdb.PubmedLinks;
import de.unijena.bioinf.fingerid.ConfidenceScore;
import de.unijena.bioinf.ms.frontend.utils.SummaryUtils;
import de.unijena.bioinf.ms.middleware.BaseApiController;
import de.unijena.bioinf.ms.middleware.SiriusContext;
import de.unijena.bioinf.ms.middleware.compounds.model.*;
import de.unijena.bioinf.ms.middleware.spectrum.AnnotatedSpectrum;
import de.unijena.bioinf.projectspace.*;
import de.unijena.bioinf.projectspace.fingerid.FBCandidatesTopK;
import de.unijena.bioinf.sirius.FTreeMetricsHelper;
import de.unijena.bioinf.sirius.scores.IsotopeScore;
import de.unijena.bioinf.sirius.scores.SiriusScore;
import de.unijena.bioinf.sirius.scores.TreeScore;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "/api/projects/{pid}")
public class CompoundController extends BaseApiController {

    //todo request list of IDs???
    @Autowired
    public CompoundController(SiriusContext context) {
        super(context);
    }


    @GetMapping(value = "/compounds", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CompoundId> getCompounds(@PathVariable String pid, @RequestParam(required = false) boolean includeSummary, @RequestParam(required = false) boolean includeMsData) {
        final SiriusProjectSpace space = projectSpace(pid).projectSpace();
        LoggerFactory.getLogger(CompoundController.class).info("Started collecting compounds...");

        final ArrayList<CompoundId> compoundIds = new ArrayList<>();
        space.iterator().forEachRemaining(ccid -> compoundIds.add(asCompoundId(ccid, pid, includeSummary, includeMsData)));

        LoggerFactory.getLogger(CompoundController.class).info("Finished parsing compounds...");
        return compoundIds;
    }

    @PostMapping(value = "/compounds", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CompoundId> importCompounds(@PathVariable String pid, @RequestParam String format, HttpServletRequest request) throws IOException {
        List<CompoundId> ids = new ArrayList<>();
        final ProjectSpaceManager space = projectSpace(pid);
        GenericParser<Ms2Experiment> parser = new MsExperimentParser().getParserByExt(format);
        try (CloseableIterator<Ms2Experiment> it = parser.parseIterator(request.getInputStream(), null)) {
            while (it.hasNext()) {
                Ms2Experiment next = it.next();
                @NotNull Instance inst = space.newCompoundWithUniqueId(next);
                ids.add(asCompoundId(inst.getID()));
            }
        }
        return ids;
    }

    @GetMapping(value = "/compounds/{cid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompoundId getCompound(@PathVariable String pid, @PathVariable String cid, @RequestParam(required = false) boolean includeSummary, @RequestParam(required = false) boolean includeMsData) {
        final SiriusProjectSpace space = projectSpace(pid).projectSpace();
        final CompoundContainerId ccid = space.findCompound(cid).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no Compound with ID '" + cid + "' in project with name '" + pid + "'"));
        return asCompoundId(ccid, pid, includeSummary, includeMsData);

    }


    private CompoundSummary asCompoundSummary(CompoundContainerId cid, String pid) {
        final ProjectSpaceManager space = projectSpace(pid);
        final Instance inst = space.newInstanceFromCompound(cid);
        return inst.loadTopFormulaResult(FormulaScoring.class, FTree.class, FBCandidatesTopK.class, CanopusResult.class)
                .map(topHit -> {
                    final CompoundSummary cSum = new CompoundSummary();
                    final FormulaScoring scorings = topHit.getAnnotationOrThrow(FormulaScoring.class);

                    //add formula summary
                    final FormulaResultSummary frs = new FormulaResultSummary();
                    cSum.setFormulaResultSummary(frs);

                    frs.setMolecularFormula(topHit.getId().getMolecularFormula().toString());
                    frs.setAdduct(topHit.getId().getIonType().toString());

                    scorings.getAnnotation(SiriusScore.class).
                            ifPresent(sscore -> frs.setSiriusScore(sscore.score()));
                    scorings.getAnnotation(IsotopeScore.class).
                            ifPresent(iscore -> frs.setIsotopeScore(iscore.score()));
                    scorings.getAnnotation(TreeScore.class).
                            ifPresent(tscore -> frs.setTreeScore(tscore.score()));
                    scorings.getAnnotation(ZodiacScore.class).
                            ifPresent(zscore -> frs.setZodiacScore(zscore.score()));

                    topHit.getAnnotation(FTree.class).
                            ifPresent(fTree -> {
                                final FTreeMetricsHelper metrHelp = new FTreeMetricsHelper(fTree);
                                frs.setNumOfexplainedPeaks(metrHelp.getNumOfExplainedPeaks());
                                frs.setNumOfexplainablePeaks(metrHelp.getNumberOfExplainablePeaks());
                                frs.setTotalExplainedIntensity(metrHelp.getExplainedIntensityRatio());
                                frs.setMedianMassDeviation(metrHelp.getMedianMassDeviation());
                            });

                    // fingerid result
                    topHit.getAnnotation(FBCandidatesTopK.class).
                            ifPresent(fbres -> {
                                final StructureResultSummary sSum = new StructureResultSummary();
                                cSum.setStructureResultSummary(sSum);

                                if (!fbres.getResults().isEmpty()) {
                                    final Scored<CompoundCandidate> can = fbres.getResults().get(0);

                                    // scores
                                    sSum.setCsiScore(can.getScore());
                                    sSum.setTanimotoSimilarity(can.getCandidate().getTanimoto());
                                    scorings.getAnnotation(ConfidenceScore.class).
                                            ifPresent(cScore -> sSum.setConfidenceScore(cScore.score()));

                                    //Structure information
                                    //check for "null" strings since the database might not be perfectly curated
                                    final String n = can.getCandidate().getName();
                                    if (n != null && !n.isEmpty() && !n.equals("null"))
                                        sSum.setStructureName(n);

                                    sSum.setSmiles(can.getCandidate().getSmiles());
                                    sSum.setInchiKey(can.getCandidate().getInchiKey2D());
                                    sSum.setXlogP(can.getCandidate().getXlogp());

                                    //meta data
                                    PubmedLinks pubMedIds = can.getCandidate().getPubmedIDs();
                                    if (pubMedIds != null)
                                        sSum.setNumOfPubMedIds(pubMedIds.getNumberOfPubmedIDs());
                                }
                            });

                    topHit.getAnnotation(CanopusResult.class).
                            ifPresent(cRes -> cSum.setCategoryResultSummary(SummaryUtils.chooseBestNPCAssignments(
                                    cRes.getNpcFingerprint().orElse(null),
                                    cRes.getCanopusFingerprint())));
                    return cSum;

                }).orElse(new CompoundSummary());
    }

    private CompoundMsData asCompoundMsData(CompoundContainerId cid, String pid) {
        try {
            //todo is reloading efficient?
            CompoundContainer compound = projectSpace(pid).projectSpace().getCompound(cid, Ms2Experiment.class);
            @NotNull Ms2Experiment experiment = compound.getAnnotationOrThrow(Ms2Experiment.class, () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compound with ID '" + compound + "' has no input Data!"));
            return new CompoundMsData(
                    opt(experiment.getMergedMs1Spectrum(), this::asSpectrum),
                    Optional.empty(),
                    experiment.getMs1Spectra().stream().map(this::asSpectrum).collect(Collectors.toList()),
                    experiment.getMs2Spectra().stream().map(this::asSpectrum).collect(Collectors.toList())
            );
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private CompoundId asCompoundId(CompoundContainerId cid, String pid, boolean includeSummary, boolean includeMsData) {
        final CompoundId compoundId = asCompoundId(cid);
        if (includeSummary)
            compoundId.setSummary(asCompoundSummary(cid, pid));
        if (includeMsData)
            compoundId.setMsData(asCompoundMsData(cid, pid));
        return compoundId;
    }

    private CompoundId asCompoundId(CompoundContainerId cid) {
        return new CompoundId(
                cid.getDirectoryName(),
                cid.getCompoundName(),
                cid.getCompoundIndex(),
                cid.getIonMass().orElse(0d),
                cid.getIonType().map(PrecursorIonType::toString).orElse(null)
        );
    }

    private <S, T> Optional<T> opt(S input, Function<S, T> convert) {
        return Optional.ofNullable(input).map(convert);
    }

    private AnnotatedSpectrum asSpectrum(Spectrum<Peak> spec) {
        return new AnnotatedSpectrum(Spectrums.copyMasses(spec), Spectrums.copyIntensities(spec), new HashMap<>());
    }


}

