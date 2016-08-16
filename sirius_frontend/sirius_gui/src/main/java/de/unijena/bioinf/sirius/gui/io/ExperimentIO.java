package de.unijena.bioinf.sirius.gui.io;

import de.unijena.bioinf.sirius.gui.structure.ExperimentContainer;

import java.io.File;
import java.util.List;

public interface ExperimentIO {

	public void save(List<ExperimentContainer> ec, File file);
	
	public List<ExperimentContainer> load(File file);

}
