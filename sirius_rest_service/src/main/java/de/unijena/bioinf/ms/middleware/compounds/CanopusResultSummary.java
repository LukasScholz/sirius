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

public class CanopusResultSummary {

    protected CompoundClass npcPathway;

    protected CompoundClass npcSuperclass;

    protected CompoundClass npcClass;

    protected CompoundClass classyFireMostSpecific;

    protected CompoundClass classyFireLevel5;

    protected CompoundClass classyFireClass;
    protected CompoundClass classyFireSubClass;

    protected CompoundClass classyFireSuperClass;



    public CompoundClass getNpcPathway() {
        return npcPathway;
    }

    public void setNpcPathway(CompoundClass npcPathway) {
        this.npcPathway = npcPathway;
    }

    public CompoundClass getNpcSuperclass() {
        return npcSuperclass;
    }

    public void setNpcSuperclass(CompoundClass npcSuperclass) {
        this.npcSuperclass = npcSuperclass;
    }

    public CompoundClass getNpcClass() {
        return npcClass;
    }

    public void setNpcClass(CompoundClass npcClass) {
        this.npcClass = npcClass;
    }

    public CompoundClass getClassyFireMostSpecific() {
        return classyFireMostSpecific;
    }

    public void setClassyFireMostSpecific(CompoundClass classyFireMostSpecific) {
        this.classyFireMostSpecific = classyFireMostSpecific;
    }

    public CompoundClass getClassyFireLevel5() {
        return classyFireLevel5;
    }

    public void setClassyFireLevel5(CompoundClass classyFireLevel5) {
        this.classyFireLevel5 = classyFireLevel5;
    }

    public CompoundClass getClassyFireClass() {
        return classyFireClass;
    }

    public void setClassyFireClass(CompoundClass classyFireClass) {
        this.classyFireClass = classyFireClass;
    }

    public CompoundClass getClassyFireSubClass() {
        return classyFireSubClass;
    }

    public void setClassyFireSubClass(CompoundClass classyFireSubClass) {
        this.classyFireSubClass = classyFireSubClass;
    }

    public CompoundClass getClassyFireSuperClass() {
        return classyFireSuperClass;
    }

    public void setClassyFireSuperClass(CompoundClass classyFireSuperClass) {
        this.classyFireSuperClass = classyFireSuperClass;
    }


}
