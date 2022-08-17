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

package de.unijena.bioinf.ms.middleware.spectrum;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.unijena.bioinf.ChemistryBase.ms.CollisionEnergy;
import de.unijena.bioinf.ChemistryBase.ms.Peak;
import de.unijena.bioinf.ChemistryBase.ms.Spectrum;
import de.unijena.bioinf.ChemistryBase.ms.utils.OrderedSpectrum;
import de.unijena.bioinf.ChemistryBase.ms.utils.Spectrums;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Iterator;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnnotatedSpectrum implements OrderedSpectrum<Peak> {
    private Integer mslevel = null;
    private CollisionEnergy collisionEnergy = null;
    private AnnotatedPeak[] peaks;

    public AnnotatedSpectrum(Spectrum<Peak> spec) {
        this(Spectrums.copyMasses(spec), Spectrums.copyIntensities(spec));
    }

    public AnnotatedSpectrum(double[] masses, double[] intensities) {
        this(masses, intensities, null);
    }

    public AnnotatedSpectrum(double[] masses, double[] intensities, @Nullable PeakAnnotation[] peakAnnotations) {
        peaks = new AnnotatedPeak[masses.length];

        if (peakAnnotations != null) {
            for (int i = 0; i < masses.length; i++)
                peaks[i] = new AnnotatedPeak(masses[i], intensities[i], peakAnnotations[i]);
        } else {
            for (int i = 0; i < masses.length; i++)
                peaks[i] = new AnnotatedPeak(masses[i], intensities[i], null);
        }
    }

    public AnnotatedPeak[] getPeaks() {
        return peaks;
    }

    public void setPeaks(AnnotatedPeak[] peaks) {
        this.peaks = peaks;
    }

    @JsonIgnore
    public double[] getMasses() {
        return Arrays.stream(peaks).mapToDouble(AnnotatedPeak::getMass).toArray();
    }

    @JsonIgnore
    public double[] getIntensities() {
        return Arrays.stream(peaks).mapToDouble(AnnotatedPeak::getIntensity).toArray();
    }

    @Override
    @JsonIgnore
    public double getMzAt(int index) {
        return peaks[index].getMass();
    }

    @Override
    @JsonIgnore
    public double getIntensityAt(int index) {
        return peaks[index].getMass();
    }

    @JsonIgnore
    public PeakAnnotation getPeakAnnotationAt(int index) {
        return peaks[index].getPeakAnnotation();
    }


    @Override
    @JsonIgnore
    public Peak getPeakAt(int index) {
        return peaks[index];
    }

    @Override
    @JsonIgnore
    public int size() {
        return peaks.length;
    }

    @NotNull
    @Override
    @JsonIgnore
    public Iterator<Peak> iterator() {
        return new Iterator<>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < peaks.length;
            }

            @Override
            public Peak next() {
                return getPeakAt(index++);
            }
        };
    }

    @Override
    @JsonIgnore
    public boolean isEmpty() {
        return OrderedSpectrum.super.isEmpty();
    }

    @Override
    public CollisionEnergy getCollisionEnergy() {
        return collisionEnergy;
    }

    @Override
    public int getMsLevel() {
        return mslevel;
    }

    public void setMslevel(int mslevel) {
        this.mslevel = mslevel;
    }

    public void setCollisionEnergy(CollisionEnergy collisionEnergy) {
        this.collisionEnergy = collisionEnergy;
    }

    @Override
    @JsonIgnore
    public double getMaxIntensity() {
        return OrderedSpectrum.super.getMaxIntensity();
    }
}
