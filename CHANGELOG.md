##### Coming soon...
- GUI: Progress information for running jobs
- GUI: More detailed Visualisation of what has already been computed
- more bugfixes ;-)

##### 4.5.0
- **feature: [CANOPUS:](https://www.biorxiv.org/content/10.1101/2020.04.17.046672v1) for negative ion mode data**
- feature: [Bayesian (individual tree)](https://doi.org/10.1093/bioinformatics/bty245) scoring is now the default for ranking structure candidates
- **update: Structure DB update due to major changes in PubChem standardization since the last one.**
  - feature: COCONUT, NORMAN and Super Natural are now officially supported
- feature: Custom-DB importer View (GUI)

- feature: mgf export for Feature Based Molecular Networking is now available in the GUI

- **breaking:**  additional columns (`ionMass`, `retentionTimeInSeconds`) have been added to project wide summary files
such as `formula_identifications.tsv`, `compound_identifications.tsv` and `compound_identifications_adducts.tsv`
- **breaking:** column names in `formula_candidates.tsv` have changed: `massError(ppm)` to `massErrorPrecursor(ppm)`, `explainedPeaks` to `numExplainedPeaks`, `medianAbsoluteMassError(ppm)` to `medianAbsoluteMassErrorFragmentPeaks(ppm)`
- **breaking:** column names describing scores now use camel case instead of underscores: `ConfidenceScore`, `SiriusScore`, `ZodiacScore`,`TreeScore`,`IsotopeScore`, `CSI:FingerIDScore`

- fix: incompatibility with recent MaOSX version caused by gatekeeper. We now provide an installable packages.
- fix: missing SCANS annotation in mgf-export subtool - creates now a valid input for FBMN
- fix: un-parsed retention times in CEF format.   
- fix: Structure DB linking (wrong ids, missing link flags, duplicate entries, etc.)
- fix: reduced memory consuption of CLI and GUI 

- JRE is now included in all version of SIRIUS
- Many more bug fixes and performance improvements 

**NOTE: SIRIUS versions will now follow semantic versioning (all upcoming releases)** 
regarding the command line interface and project-space output.  

##### 4.4.29
- fix: Error when parsing FragTree json with non numeric double values
- fix: layout of screener progress bar on Mac  

##### 4.4.28
- feature: Retention time will now be imported by SIRIUS 
  - RT is shown in the Compound list in the SIRUS GUI and the list can be sorted by RT
  - RT is part of the compound.info file in the project-space 
- feature: Loglevel can now be changed from CLI
- feature: Summaries can not be written without closing SIRIUS GUI
  - Improvement: Better progress reporting when Summary writing summaries (GUI)  
- fix: Agilent CEF files without CE can now be imported

##### 4.4.27
- feature: coin-or ilp solver (CLP) is now included. This allows parallel computation of FragTrees without the need for a commercial solver.
- improvement: Compounds without given charge are can now be imported. SIRIUS tries to guess the charge from the name (keyword: pos/neg) or falls back to positive.
- improvement: additional parameters in compute dialog
- improvement: commands of the 'show command' dialog can now be copied
- fix: error when writing/reading fragmentation trees with new Jackson parser
- fix: mgf exporter (CLI) now outputs feature name properly
- fix: deadlock during connection check without internet connection
- fix: tree rendering bug on non linux systems
- fix: crash when aborting recompute dialog
- upgrade (GUI): included JRE to `zulu11.41.23-ca-fx-jre11.0.8`

##### 4.4.26
- fix: deadlock and waiting time due to webservice connections
- fix/improvement: Adduct Settings and Adduct detection
- fix: memory leak in third party json lib -> Zodiac memory consumption has been reduced dramatically 
- fix: several minor bug fixes in the sirius libs

##### 4.4.25
- fix: removed spring boot packaging to
  - solve several class not found issues, 
  - solve github issue [#7](https://github.com/boecker-lab/sirius/issues/7)
  - errors when importing and aligning mzml files. 
  - improve startup time
- fix: cosine similarity tool ignores instances without spectra (failed before)
- fix: mgf-export tool skips invalid instances if possible (failed before)
- instance validation after lcms-align tool
##### 4.4.24
- feature: ms2 istotope scorer now available in cli and gui

##### 4.4.23
- fix: wrong missing value handling in xlogp filter (some candidates were invisible)
- improvement: less cores for computations if gui is running to have mor cpu time for GUI tasks
- improvement:  show deviation to target ion in FragTree root if precursor is missing in MS/MS 

##### 4.4.22
- fix: Classloader exceptions when using CLI from the GUI version
- fix: Wrong mass deviation for trees with adducts
- fix: misplaced labels when exporting svg/pdf fragtrees
- fix: some minor GUI bugs

##### 4.4.21
- fix: incompatibilities with existing configs from previous versions (.sirius)
- fix: CANOPUS detail view has size zero
- fix: failing CSI:FingerID computation with Zodiac re-ranking and existing Adducts
- improvement: errors that occur before GUI is started are now reported
- improvement: minor GUI improvements
##### 4.4.20
- fix: some more fixes on MacOS GUI freezes 

##### 4.4.18
- fix: GUI Deadlock on MacOS X fixed. **Mac version is now available**.
- improvement: Character separated files in project-space have now .tsv extension for better excel compatibility.
- feature: Windows headless executable respects `%JAVA_HOME%` as JRE location.
- improvement: Improved packaging and startup of the GUI version
- fixes GitHub issues: [4](https://github.com/boecker-lab/sirius/issues/4) and [6](https://github.com/boecker-lab/sirius/issues/6)

##### 4.4.16
- feature: **CSI:FingerID for negative ion mode is available**
  - NOTE: CANOPUS for negative mode data is not ready yet and will still take some time.
- fix: Too small Heapsize on Windows
- improvement: better GUI performance 

##### 4.4.15
- feature: CLI Sub-Tool to export projects to mgf.
- feature: multiple candidate number for Zodiac.
- fix: zodiac score rendering.
- fix: deadlock project-space import
- fixes: tree rendering
- improvement: import and deletion performance
- improvement: import progress now shown

##### 4.4.14
- fix: MacOS included JRE not found.
- fix: ignored parameters.
- fix: recompute does not correctly invalidate and delete previous results.
- fix: UI now correctly update when data will by deleted by the computations.

#### 4.4.(0-13)
- **New (and newly integrated) tools:**
  - [**CANOPUS:**](https://www.biorxiv.org/content/10.1101/2020.04.17.046672v1): A tool for the comprehensive annotation of compound classes from MS/MS data.
  - [**ZODIAC:**](https://www.biorxiv.org/content/10.1101/842740v1) Builds upon the SIRIUS molecular formula identifications and uses, say, its top 50 molecular formula 
    annotations as candidates for one compound. It then re-ranks molecular formula candidates using Bayesian statistics.
  - [**PASSATUTTO:**](https://www.nature.com/articles/s41467-017-01318-5) Is now part of SIRIUS and allows you to generate dataset specific decoy databases from computed fragmentation trees. 
  - Other handy standalone tools e.g. compound similarity calculation, mass decomposition, custom-db creation and project-space manipulation. 

- [**Project-Space:**](https://link.springer.com/protocol/10.1007/978-1-0716-0239-3_11) A standardized persistence layer shared by CLI and GUI that makes both fully compatible.
  - Save and reimport your projects with all previously calculated results.
  - Review your results computed with the CLI in the GUI.
  - Handy project-space summary CSV and mzTab-M files for downstream analysis.
  - Preojects can be stored and modified as directory structure or as compressed archive. 
    
- **LCMS-Runs:** SIRIUS can now handle full LCMS-Runs given in mzML/mzXML format and performs automatic feature detection. 
  - The **lcms-align** preprocessing tool performs feature detection and feature alignment for multiple LCMS-Runs based on the available the MS/MS spectra.
    
- Redesigned **Command line interface**: SIRIUS is now a toolbox containing many 
subtools that may be combined to ToolChains based on the project-space.

- **CSI:FingerID** had some massive updates, including more and larger molecular properties. 
  - **Structure DBs** New version of the CSI:FingerID PubChem copy that now uses **PubChem standardized structures**.
  - [**NORMAN**](https://www.norman-network.com/nds/common/) is now available as search DB
  - All available database filters can now be combined to arbitrary subsets for searching (even with custom databases).      

- **Interactive fragmentation tree viewer** with vector graphics export in the GUI.
- New REST service with [openAPI](https://www.csi-fingerid.uni-jena.de/v1.4.2-SNAPSHOT/v2/api-docs) specification and [Swagger-UI](https://www.csi-fingerid.uni-jena.de/v1.4.2-SNAPSHOT/swagger-ui.html).
- **Java 11** or higher is now mandatory
  - **GUI** version ships with an **integrated JRE**
- Many minor improvements and Bugfixes

#### 4.0.1
-   **Java 9 and higher are now supported**
-   **CSI:FingerID trainings structures available**
    - Trainings structures available via WebAPI.
    - Trainings structures are flagged in CSI:FingerID candidate list.
-   **SMARTS filter for candidate list (GUI)**
-   **Molecular Property filter for candidate list (GUI)**    
-   **Available prediction workers of the CSI:FingerID webservice can be listed from SIRIUS**
-   Improved connection handling and auto reconnect to Webservice
-   Improved error messaged    
-   Improved stability and load balancing of the CSI:FingerID webservice
-   Several bug fixes

#### 4.0
-   **Fragmentation tree heuristics**
-   **Negative ion mode data is now supported**
-   **Polished and more informative GUI**
    - **Sirius Overview:** Explained intensity, number of explained peaks, median mass deviation  
    - **Fragmentation trees:** Color coding of nodes by intensity/mass deviation,
      more informative Fragmentation tree nodes
    - **CSI:FingerID Overview:** Number of Pubmed publication with pubmed linking for each Candidate,
      Visualization of CSI:FingerID score.
    - **Predicted Fingerprints:** Visualisation of prediction (posterior probability), predictor quality (F1)
      and number of training examples.    
    - Several small improvements                
-   **CPLEX** ILP solver support
-   Consider a specific list of **ionizations for Sirius**
-   Consider a specific list of **adducts for CSI:FingerID**
-   Custom ionizations/adducts can be specified (CLI and GUI)
-   **Full-featured** standalone **command line version** (headless
    version)
-   Improved **parallelization** and task management
-   Improved stability of the CSI:FingerID webservice
-   Time limit for fragmentation tree computations
-   Specify fields to import name and ID from .sdf into a custom
    database (GUI).
-   CSI:FingerID results can be **filtered by Custom databases** (GUI).
-   Better filtering performance (GUI)
-   Bug fix in Database filtering view (GUI)
-   Error Reporter bug fixed (GUI)
-   Logging bugs fixed
-   Many minor bug fixes

#### 3.5
-   **Custom databases** can be imported by hand or via csv file. You
    can manage multiple databases within Sirius.
-   New **Bayesian Network scoring** for CSI:FingerID which takes
    dependencies between molecular properties into account.
-   **CSI:FingerID Overview** which lists results for all molecular
    formulas.
-   **Visualization of the predicted fingerprints**.
-   **ECFP fingerprints** are now also in the CSI:FingerID database and
    do no longer have to be computed on the users side.
-   Connection error detection and refresh feature. No restart required
    to apply Sirius internal proxy settings anymore.
-   **System wide proxy** settings are now supported.
-   Many minor bug fixes and small improvements of the GUI
#### 3.4
-   **element prediction** using isotope pattern
-   CSI:FingerID now predicts **more molecular properties** which
    improves structure identification
-   improved structure of the result output generated by the command
    line tool **to its final version**
#### 3.3
-   fix missing MS2 data error
-   MacOSX compatible start script
-   add proxy settings, bug reporter, feature request
-   new GUI look

#### 3.2
-   integration of CSI:FingerID and structure identification into SIRIUS
-   it is now possible to search formulas or structures in molecular
    databases
-   isotope pattern analysis is now rewritten and hopefully more stable
    than before

#### 3.1.3
-   fix bug with penalizing molecular formulas on intrinsically charged
    mode
-   fix critical bug in CSV reader

#### 3.1.0
-   Sirius User Interface
-   new output type *-O sirius*. The .sirius format can be imported into
    the User Interface.
-   Experimental support for in-source fragmentations and adducts

#### 3.0.3
-   fix crash when using GLPK solver

#### 3.0.2
-   fix bug: SIRIUS uses the old scoring system by default when *-p*
    parameter is not given
-   fix some minor bugs

#### 3.0.1
-   if MS1 data is available, SIRIUS will now always use the parent peak
    from MS1 to decompose the parent ion, instead of using the peak from
    an MS/MS spectrum
-   fix bugs in isotope pattern selection
-   SIRIUS ships now with the correct version of the GLPK binary

#### 3.0.0
-   release version