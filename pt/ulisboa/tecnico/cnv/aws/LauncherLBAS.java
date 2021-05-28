package pt.ulisboa.tecnico.cnv.aws;

public class LauncherLBAS {

    public static void main(final String[] args) throws Exception {

        if(args.length < 2 ){
            System.err.println("ERROR: Wrong number of arguments");
            return;
        }
        LoadBalancer.init();
        LoadBalancer.execute(args);

        Thread as = new Thread() {
            public void run() {
                try {
                    AutoScaler.init();
                    AutoScaler.execute();
                } catch(Exception e) {
                    System.err.println(e.getMessage());
                }
            }
        };

        as.start();
    }
}