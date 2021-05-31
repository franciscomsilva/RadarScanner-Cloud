# RadarScanner@Cloud

Radar Scanner Cloud developed as final course project for the Cloud Computing & Virtualization course @ IST Lisbon

Developed by [Francisco Silva](https://github.com/franciscomsilva), [Guilherme Cardoso](https://github.com/GascPT) and [Tiago Domingues](https://github.com/Dr0g0n).



## AWS SDK Installation

Since AWS only provides the latest version of the SDK in pre-built form, and in order to avoid version mismatch between the delivery of the project
and the actual execution, we uploaded the version of the SDK we used to the ULisboa Google Drive to facilitate the building process. The SDK
is available for download at: https://drive.google.com/file/d/1QnI7LcJcnrwjgD52U2tMEG69IV0DTeEk/view?usp=sharing

After downloading, the .zip file should be extracted to the root directory (`~/cnv-project/`)

# Build and Installation

This project runs on **Java Version 1.7.0_80**.

In the `~/cnv-project/scripts/` directory several scripts are provided to build the solution. To build the complete system, use the
`~/cnv-project/scripts/config-all.sh` script. Run it from the root directory (`~/cnv-project/`). This will build and instrument the solution fully. 

It is also necessary to define the correct classpaths. To define this execute the following commands:

`export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS`

`export CLASSPATH="/home/ec2-user/cnv-project:/home/ec2-user/cnv-project/aws-java-sdk/lib/aws-java-sdk-1.11.1029.jar:/home/ec2-user/cnv-project/aws-java-sdk/third-party/lib/*:./"`

To run the WebServer simply run (from the root directory): `java pt.ulisboa.tecnico.cnv.server.WebServer -address 0.0.0.0`

To run both the LoadBalancer and the Auto Scaler (from the root directory): `java pt.ulisboa.tecnico.cnv.aws.LauncherLBAS -address 0.0.0.0`

We also have, in the `~/cnv-project/scripts/` directory a file named `rc.local.sample` which corresponds to the rc.local file we
placed in the Amazon Instances of the WebServer.

## Structure of folders

- `BIT` folder: contains the code for the BIT Tool
- `datasets` folder: contains the map images for the Solver application
- `scripts` folder: all the scripts used to build the project
- `pt/ulisboa/tecnico/cnv/aws` folder: contains the code for the AWS related operation (DynamoHandler, LoadBalancer and AutoScaler)
- `pt/ulisboa/tecnico/cnv/BIT/tools` folder: instrumentation tools developed for the application
- `pt/ulisboa/tecnico/cnv/server` folder: the WebServer code
- `pt/ulisboa/tecnico/cnv/solver` folder: the instrumented and original Solver code