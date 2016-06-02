Changelog
**********************

3.1
 * Sirius User Interface
 * new output type **-O sirius**. The .sirius format can be imported into the User Interface.
 * Experimental support for in-source fragmentations and adducts

3.0.3
  * fix crash when using GLPK solver

3.0.2
  * fix bug: SIRIUS uses the old scoring system by default when -p parameter is not given
  * fix some minor bugs

3.0.1
  * if MS1 data is available, SIRIUS will now always use the parent peak from MS1 to decompose the parent ion, instead of using the peak from an MS/MS spectrum
  * fix bugs in isotope pattern selection
  * SIRIUS ships now with the correct version of the GLPK binary

3.0.0
  * release version
