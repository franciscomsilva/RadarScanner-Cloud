#!/bin/bash


javac pt/ulisboa/tecnico/cnv/BIT/tools/*.java
java pt.ulisboa.tecnico.cnv.BIT.tools.ICount pt/ulisboa/tecnico/cnv/solver/backup pt/ulisboa/tecnico/cnv/solver/output/
cp pt/ulisboa/tecnico/cnv/solver/output/* pt/ulisboa/tecnico/cnv/solver/
javac pt/ulisboa/tecnico/cnv/server/*.java
