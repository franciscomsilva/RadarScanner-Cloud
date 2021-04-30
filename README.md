# RadarScanner@Cloud

Radar Scanner Cloud developed as final course project for the Cloud Computing & Virtualization course @ IST Lisbon

Developed by [Francisco Silva](https://github.com/franciscomsilva), [Guilherme Cardoso](https://github.com/GascPT) and [Tiago Domingues](https://github.com/Dr0g0n).


## Architecture

Currently, the architecture of the system is organized in three different components:
- AWS Load Balancer;
- AWS Auto-scaler;
- Web Servers.

The AWS Load Balancer is the entry point of the system. It receives all web requests, and
for each one, it selects an active web server from the cluster nodes to serve the request and forwards it to the selected server.

The Auto-scaler component consists of an AWS Auto-scaling group that increases and reduces the number of active web server machines according to the load of the system, measured in percentage of CPU utilization.

The Web Servers are system virtual machines running an off-the-shelf Java-based web
server application on top of Linux. Each one runs on an AWS Elastic Compute Cloud (EC2) instance and receives web requests to perform radar scanning tasks. The page serving the requests will perform the scanning and assessing online and, once it is complete, reply to the web request. 


## AWS System Configurations

### AWS Load Balancer

The only parameter that was configured in the creation of the Load Balancer was the forwarding of the traffic. In this case, the LB receives HTTP requests on port 80 and forwards them to the Web Servers through HTTP on port 8000.


### AWS Auto Scaling Group

Currently, the Auto Scaling Group has a minimum capacity of 1 instance and a maximum capacity of 4 instances. The Auto Scaler manages the criation/destruction of VM intances based on the total CPU utilization. On the one hand, if the total CPU utilization of the cluster exceeds 50%, the Auto Scaler is going to add another web server instance. On the other hand, if the total CPU utilization of the cluster is below 25%, the Auto Scaler is going to remove one web server instance.