#!/bin/bash

export CLASSPATH="/home/vagrant/cnv/RadarScanner-Cloud:./"
javac pt/ulisboa/tecnico/cnv/BIT/samples/*.java
java pt.ulisboa.tecnico.cnv.BIT.samples.ICount pt/ulisboa/tecnico/cnv/solver/backup pt/ulisboa/tecnico/cnv/solver/output/
cp pt/ulisboa/tecnico/cnv/solver/output/* pt/ulisboa/tecnico/cnv/solver/
javac pt/ulisboa/tecnico/cnv/server/*.java
