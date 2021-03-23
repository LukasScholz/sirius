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

package de.unijena.bioinf.projectspace.summaries;

public interface SummaryLocations {
    String
            MZTAB_SUMMARY = "report.mztab",
            FORMULA_CANDIDATES = "formula_candidates.tsv",
            STRUCTURE_CANDIDATES = "structure_candidates.tsv",
            STRUCTURE_CANDIDATES_TOP = "structure_candidates_topHits.tsv",
            FORMULA_SUMMARY = "formula_identifications.tsv",
            COMPOUND_SUMMARY = "compound_identifications.tsv",
            COMPOUND_SUMMARY_ADDUCTS = "compound_identifications_adducts.tsv",
            CANOPUS_SUMMARY = "canopus_summary.tsv",
            CANOPUS_SUMMARY_ADDUCT = "canopus_summary_adducts.tsv";
}
