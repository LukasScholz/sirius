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

package de.unijena.bioinf.ms.frontend;

import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

import java.util.function.Function;

public class DefaultParameter {
    public final String value;

    public DefaultParameter(String value) {
        this.value = value;
    }

    public static class Converter implements CommandLine.ITypeConverter<DefaultParameter> {
        @Override
        public DefaultParameter convert(String value) throws Exception {
            return new DefaultParameter(value);
        }
    }

    public String asString() {
        return value;
    }

    private <T> T parse(Function<String, T> doParsing) {
        if (value == null)
            return null;
        return doParsing.apply(value);
    }

    public Boolean asBoolean() {
        return parse(Boolean::parseBoolean);
    }

    public Double asDouble() {
        return parse(Double::parseDouble);
    }

    public Float asFloat() {
        return parse(Float::parseFloat);
    }

    public Long asLong() {
        return parse(Long::parseLong);
    }

    public Integer asInt() {
        return parse(Integer::parseInt);
    }

    public <T extends Enum<T>> T asEnum(@NotNull Class<T> enumType) {
        return parse(it -> Enum.valueOf(enumType, it));
    }

    public DefaultParameter invertBool() {
        return new DefaultParameter(value == null ? null : asBoolean() ? "false" : "true");
    }

    @Override
    public String toString() {
        return asString();
    }
}
