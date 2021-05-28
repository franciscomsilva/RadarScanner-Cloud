package pt.ulisboa.tecnico.cnv.server;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.solver.*;
import pt.ulisboa.tecnico.cnv.BIT.tools.*;

import pt.ulisboa.tecnico.cnv.aws.*;


import javax.imageio.ImageIO;

public class WebServer {

	static ServerArgumentParser sap = null;

	public static void main(final String[] args) throws Exception {



		try {
			// Get user-provided flags.
			WebServer.sap = new ServerArgumentParser(args);
		}
		catch(Exception e) {
			System.out.println(e);
			return;
		}




		System.out.println("> Finished parsing Server args.");

		//final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8000), 0);

		final HttpServer server = HttpServer.create(new InetSocketAddress(WebServer.sap.getServerAddress(), WebServer.sap.getServerPort()), 0);



		server.createContext("/scan", new MyHandler());
		server.createContext("/test", new TestHandler());



		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		System.out.println(server.getAddress().toString());

	}

	static class TestHandler implements HttpHandler {

		@Override
		public void handle(final HttpExchange t) throws IOException {
			t.sendResponseHeaders(200, -1);
		}
	}

	static class MyHandler implements HttpHandler {


		@Override
		public void handle(final HttpExchange t) throws IOException {
			// Get the query.
			final String query = t.getRequestURI().getQuery();

			System.out.println("> Query:\t" + query);

			// Break it down into String[].
			final String[] params = query.split("&");



			// Store as if it was a direct call to SolverMain.
			final ArrayList<String> newArgs = new ArrayList<>();
			for (final String p : params) {
				final String[] splitParam = p.split("=");

				if(splitParam[0].equals("i")) {
					splitParam[1] = WebServer.sap.getMapsDirectory() + "/" + splitParam[1];
				}

				newArgs.add("-" + splitParam[0]);
				newArgs.add(splitParam[1]);

				/*
				System.out.println("splitParam[0]: " + splitParam[0]);
				System.out.println("splitParam[1]: " + splitParam[1]);
				*/
			}

			if(sap.isDebugging()) {
				newArgs.add("-d");
			}


			// Store from ArrayList into regular String[].
			final String[] args = new String[newArgs.size()];
			int i = 0;
			for(String arg: newArgs) {
				args[i] = arg;
				i++;
			}


			/*
			for(String ar : args) {
				System.out.println("ar: " + ar);
			} */



			// Create solver instance from factory.
			final Solver s = SolverFactory.getInstance().makeSolver(args);

			if(s == null) {
				System.out.println("> Problem creating Solver. Exiting.");
				System.exit(1);
			}

			// Write figure file to disk.
			File responseFile = null;
			try {

				final BufferedImage outputImg = s.solveImage();

				final String outPath = WebServer.sap.getOutputDirectory();

				final String imageName = s.toString();

				/*
				if(ap.isDebugging()) {
					System.out.println("> Image name: " + imageName);
				} */

				final Path imagePathPNG = Paths.get(outPath, imageName);
				ImageIO.write(outputImg, "png", imagePathPNG.toFile());

				responseFile = imagePathPNG.toFile();

			} catch (final FileNotFoundException e) {
				e.printStackTrace();
			} catch (final IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}



			// Send response to browser.
			final Headers hdrs = t.getResponseHeaders();

			hdrs.add("Content-Type", "image/png");

			hdrs.add("Access-Control-Allow-Origin", "*");
			hdrs.add("Access-Control-Allow-Credentials", "true");
			hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
			hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
			
			t.sendResponseHeaders(200, responseFile.length());

			final OutputStream os = t.getResponseBody();
			Files.copy(responseFile.toPath(), os);
			os.close();

			System.out.println("> Sent response to " + t.getRemoteAddress().toString());


			//GET AND PRINT METRICS
			long thread_id = Thread.currentThread().getId();
			int i_count = ICount.getICount(thread_id);
			int load_count = LoadStoreCount.getLoadCount(thread_id);
			int store_count = LoadStoreCount.getStoreCount(thread_id);
			//int new_count =  AllocCount.getNewCount(thread_id);
			//int new_array_reference_count = AllocCount.getANewArrayCount(thread_id);

			/*
			System.out.println("Thread ID: " + String.valueOf(thread_id));
			System.out.println("Number of instructions: " + i_count);
			System.out.println("Number of Loads: " +load_count);
			System.out.println("Number of Store: " + store_count);
			System.out.println("Number of New Variables: " + new_count);
			System.out.println("Number of New Arrays of Reference: " + new_array_reference_count);
			 */

			//RETRIEVE QUERY ARGUMENTS
			int y1 = Integer.parseInt(params[5].split("y1=")[1]);
			int y0 = Integer.parseInt(params[4].split("y0=")[1]);
			int x1 = Integer.parseInt(params[3].split("x1=")[1]);
			int x0 = Integer.parseInt(params[2].split("x0=")[1]);

			int height = Integer.parseInt(params[1].split("h=")[1]);
			int width = Integer.parseInt(params[0].split("w=")[1]);
			String scan_type = params[8].split("s=")[1];
			String map_image = params[9].split("i=")[1];

			/*
			System.out.println(height);
			System.out.println(width);
			System.out.println(scan_type);
			System.out.println(map_image);
			*/


			int area = (y1 - y0) * (x1-x0);

			//WRITES METRICS ALONG WITH QUERY ARGUMENTS TO FILE
			/*
			try {
				String data =  i_count + "," + load_count + "," + store_count + "," + new_count + "," + new_array_reference_count + "," +
						height + "," + width + "," + area + "," + scan_type + "," + map_image + "\n";
				File f1 = new File(METRICS_FILE);
				if(!f1.exists()) {
					f1.createNewFile();
				}
				FileWriter fileWritter = new FileWriter(METRICS_FILE,true);
				BufferedWriter bw = new BufferedWriter(fileWritter);
				bw.write(data);
				bw.close();
			} catch(IOException e){
				e.printStackTrace();
			}*/

			//WRITES METRICS AND QUERY ARGUMENTS TO DYNAMODB
			try{
				DynamoHandler.init();
				//DynamoHandler.newMetrics(i_count,load_count,store_count,new_count,new_array_reference_count,height,width,area,scan_type,map_image);
				DynamoHandler.newMetrics(i_count,load_count,store_count,height,width,area,scan_type,map_image);
			}catch(Exception e){
				System.err.println(e.getMessage());
				return;
			}


			//RESETS COUNTS
			ICount.reset(thread_id);
			LoadStoreCount.reset(thread_id);
			//AllocCount.reset(thread_id);







		}
	}



}
