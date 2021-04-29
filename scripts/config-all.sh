#!/bin/bash

export CLASSPATH="/home/vagrant/cnv/cnv-project:./"
javac pt/ulisboa/tecnico/cnv/BIT/tools/*.java
java pt.ulisboa.tecnico.cnv.BIT.tools.LoadStoreCount pt/ulisboa/tecnico/cnv/solver/backup pt/ulisboa/tecnico/cnv/solver/output/
mv pt/ulisboa/tecnico/cnv/solver/output/* pt/ulisboa/tecnico/cnv/solver/
java pt.ulisboa.tecnico.cnv.BIT.tools.AllocCount pt/ulisboa/tecnico/cnv/solver pt/ulisboa/tecnico/cnv/solver/output
mv pt/ulisboa/tecnico/cnv/solver/output/* pt/ulisboa/tecnico/cnv/solver/
java pt.ulisboa.tecnico.cnv.BIT.tools.ICount pt/ulisboa/tecnico/cnv/solver pt/ulisboa/tecnico/cnv/solver/output/
mv pt/ulisboa/tecnico/cnv/solver/output/* pt/ulisboa/tecnico/cnv/solver/
javac pt/ulisboa/tecnico/cnv/server/*.java
