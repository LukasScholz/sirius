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

package de.unijena.bioinf.ms.middleware.formulas;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unijena.bioinf.ChemistryBase.algorithm.scoring.Scored;
import de.unijena.bioinf.ChemistryBase.ms.ft.FTree;
import de.unijena.bioinf.babelms.json.FTJsonWriter;
import de.unijena.bioinf.chemdb.CompoundCandidate;
import de.unijena.bioinf.fingerid.FingerprintResult;
import de.unijena.bioinf.fingerid.blast.FBCandidates;
import de.unijena.bioinf.ms.annotations.DataAnnotation;
import de.unijena.bioinf.ms.middleware.BaseApiController;
import de.unijena.bioinf.ms.middleware.SiriusContext;
import de.unijena.bioinf.projectspace.FormulaScoring;
import de.unijena.bioinf.projectspace.SiriusProjectSpace;
import de.unijena.bioinf.projectspace.CompoundContainer;
import de.unijena.bioinf.projectspace.FormulaResult;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping(value = "/api/projects/{pid}/compounds/{cid}")
public class FormulaResultController extends BaseApiController {

    @Autowired
    public FormulaResultController(SiriusContext context) {
        super(context);
    }

    @GetMapping(value = "/formulas", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public List<FormulaId> getFormulaIds(@PathVariable String pid, @PathVariable String cid, @RequestParam(required = false) boolean includeFormulaScores) {
        SiriusProjectSpace space = projectSpace(pid);
        LoggerFactory.getLogger(FormulaResultController.class).info("Started collecting formulas...");
        return getCompound(space ,cid).map(con -> con.getResultsRO().values().stream().map(frId -> {
            try{
                return space.getFormulaResult(frId, FormulaScoring.class);
            } catch (IOException e) {
                return null;
            }
        }).map(fr -> {
            FormulaId formulaId = new FormulaId(fr.getId());
            if(includeFormulaScores){
                fr.getAnnotation(FormulaScoring.class).ifPresent(fs -> formulaId.setResultScores(new FormulaResultScores(fs)));
            }
            return formulaId;
        }).collect(Collectors.toList())).orElse(Collections.emptyList());
    }

    @GetMapping(value = "/formulas/{fid}", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public FormulaId getFormulaResult(@PathVariable String pid, @PathVariable String cid, @PathVariable String fid) {
        SiriusProjectSpace space = projectSpace(pid);
        return this.getAnnotatedFormulaResult(space,cid,fid,FormulaScoring.class).
                map(fr -> {
                    FormulaId formulaId = new FormulaId(fr.getId());
                    fr.getAnnotation(FormulaScoring.class).ifPresent(fs -> formulaId.setResultScores(new FormulaResultScores(fs)));
                    return formulaId;
        }).orElse(null);
    }

    @GetMapping(value = "/formulas/{fid}/scores", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public FormulaResultScores getFormulaResultScores(@PathVariable String pid, @PathVariable String cid, @PathVariable String fid){
        SiriusProjectSpace projectSpace = projectSpace(pid);
        return this.getAnnotatedFormulaResult(projectSpace,cid,fid,FormulaScoring.class).map(fr -> {
            FormulaScoring formulaScoring = fr.getAnnotation(FormulaScoring.class).orElse(null);
            return new FormulaResultScores(formulaScoring);
        }).orElse(null);
    }

    @GetMapping(value = "/formulas/{fid}/candidates", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public String getStructureCandidates(@PathVariable String pid, @PathVariable String cid, @PathVariable String fid){
        SiriusProjectSpace projectSpace = projectSpace(pid);
        ObjectMapper mapper = new ObjectMapper();
        return this.getAnnotatedFormulaResult(projectSpace, cid, fid, FBCandidates.class).map(fr -> {
            List<String> jsons = fr.getAnnotation(FBCandidates.class).map(candidates ->
                    candidates.getResults().stream().map(sc -> {
                        try {
                            return mapper.writeValueAsString(sc.getCandidate());
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }).collect(Collectors.toList()))
                    .orElse(Collections.emptyList());
            return this.JSONListToOneJSON(jsons);
        }).orElse(null);
    }

    @GetMapping(value = "/formulas/topHitCandidate", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public String getTopHitCandidate(@PathVariable String pid, @PathVariable String cid) throws JsonProcessingException {
        SiriusProjectSpace projectSpace = projectSpace(pid);
        Stream<Optional<FormulaResult>> annotatedFResults = this.getCompound(projectSpace,cid).map(cc ->
                cc.getResultsRO().values().stream().map(frId -> {
                    try {
                        return Optional.of(projectSpace.getFormulaResult(frId, FBCandidates.class));
                    } catch (IOException e) {
                        return Optional.<FormulaResult>empty();
                    }
                })).orElse(Stream.empty());
        /*
         * two cases: there is no CompoundContainer in the projectspace with the specified identifier 'cid'
         * or: the CompoundContainer does not contain FormulaResults --> cc.getResults().values() is an empty collection
         *
         * If the stream is not empty, the elements are Optional<FormulaResult> objects.
         */
        List<Scored<CompoundCandidate>> topHits = annotatedFResults.map(optfr ->
                optfr.map(fr -> fr.getAnnotation(FBCandidates.class).map(fbCandidates ->
                        fbCandidates.getResults().get(0)).orElse(null))).
                filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());

        // topHits can be an empty list
        if(topHits.isEmpty()) return null;

        Scored<CompoundCandidate> bestCandidate = topHits.get(0);
        for(int idx = 1; idx < topHits.size(); idx++){
            if(topHits.get(idx).getScore() > bestCandidate.getScore()){
                bestCandidate = topHits.get(idx);
            }
        }
        return new ObjectMapper().writeValueAsString(bestCandidate.getCandidate());
    }

    @GetMapping(value = "formulas/{fid}/tree", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public String getFragmentationTree(@PathVariable String pid, @PathVariable String cid, @PathVariable String fid){
        SiriusProjectSpace projectSpace = super.projectSpace(pid);
        Optional<FormulaResult> fResult = this.getAnnotatedFormulaResult(projectSpace, cid, fid, FTree.class);
        FTree fTree = fResult.map(fr -> fr.getAnnotation(FTree.class).orElse(null)).orElse(null);
        FTJsonWriter ftWriter = new FTJsonWriter();
        return ftWriter.treeToJsonString(fTree);
    }

    @GetMapping(value = "formulas/{fid}/fingerprint", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public double[] getFingerprint(@PathVariable String pid, @PathVariable String cid, @PathVariable String fid, @RequestParam(required = false) boolean asDeterministic){
        SiriusProjectSpace projectSpace = super.projectSpace(pid);
        Optional<FormulaResult> fResult = this.getAnnotatedFormulaResult(projectSpace, cid, fid, FingerprintResult.class);
        return fResult.map(fr -> fr.getAnnotation(FingerprintResult.class).
                map(fpResult -> {
                    if(asDeterministic){
                        boolean[] boolFingerprint = fpResult.fingerprint.asDeterministic().toBooleanArray();
                        double[] fingerprint = new double[boolFingerprint.length];
                        for(int idx = 0; idx < fingerprint.length; idx++){
                            fingerprint[idx] = boolFingerprint[idx] ? 1 : 0;
                        }
                        return fingerprint;
                    }else{
                        return fpResult.fingerprint.toProbabilityArray();
                    }
                }).orElse(null)).orElse(null);
    }

    @SafeVarargs
    private Optional<FormulaResult> getAnnotatedFormulaResult(SiriusProjectSpace projectSpace, String cid, String fid, Class<? extends DataAnnotation>... components){
        Optional<FormulaResult> fResult = this.getCompound(projectSpace, cid).map(cc -> cc.findResult(fid).map(frId -> {
            try {
                return projectSpace.getFormulaResult(frId, components);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }).orElse(null));
        return fResult;
    }

    private Optional<CompoundContainer> getCompound(SiriusProjectSpace space, String cid, Class<? extends DataAnnotation>... components) {
        return space.findCompound(cid).map(ccId -> {
            try {
                return space.getCompound(ccId, components);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    private String JSONListToOneJSON(List<String> jsons){
        StringBuilder builder = new StringBuilder("[");
        if(!jsons.isEmpty()) builder.append(jsons.get(0));

        for(int idx = 1; idx < jsons.size(); idx++){
            builder.append(",");
            builder.append(jsons.get(idx));
        }
        builder.append("]");
        return builder.toString();
    }

}

