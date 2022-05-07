package edu.nmsu.cs.webserver;

/**
 * Web worker: an object of this class executes in its own new thread to receive and respond to a
 * single HTTP request. After the constructor the object executes on its "run" method, and leaves
 * when it is done.
 *
 * One WebWorker object is only responsible for one client connection. This code uses Java threads
 * to parallelize the handling of clients: each WebWorker runs in its own thread. This means that
 * you can essentially just think about what is happening on one client at a time, ignoring the fact
 * that the entirety of the webserver execution might be handling other clients, too.
 *
 * This WebWorker class (i.e., an object of this class) is where all the client interaction is done.
 * The "run()" method is the beginning -- think of it as the "main()" for a client interaction. It
 * does three things in a row, invoking three methods in this class: it reads the incoming HTTP
 * request; it writes out an HTTP header to begin its response, and then it writes out some HTML
 * content for the response content. HTTP requests and responses are just lines of text (in a very
 * particular format).
 *
 * @author Jon Cook, Ph.D.
 *
 **/

import java.io.BufferedReader;
import java.io.InputStream;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.io.IOException;
import java.io.File;
import java.util.Scanner;
import java.time.LocalDate;
import java.util.HashMap;
import java.io.*;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.nio.file.Files;


public class WebWorker implements Runnable {

	private Socket socket;
	private boolean landingPage = false;

	/**
	 * Constructor: must have a valid open socket
	 **/
	public WebWorker(Socket s) {
		socket = s;
	}

	/**
	 * Worker thread starting point. Each worker handles just one HTTP request and then returns, which
	 * destroys the thread. This method assumes that whoever created the worker created it with a
	 * valid open socket object.
	 **/
	public void run() {

		System.err.println("Handling connection...");

		try {

			//create IO Stream
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();

			//process streams
			File request = readHTTPRequest(is);

			//now we have to check the content type
			String typeOfContent = checktypeOfContent(request);
			writeHTTPHeader(os, typeOfContent, request);
			writeContent(os, request, typeOfContent);
			os.flush();
			socket.close();
		}

		catch (Exception e) {
			System.err.println("Output error: " + e);
		}

		System.err.println("Done handling connection.");
		return;

	}

	/**
	 * Read the HTTP request header.
	 **/
	private File readHTTPRequest(InputStream is) {

		String line;
		String request = null;
		BufferedReader r = new BufferedReader(new InputStreamReader(is));

		while(true){
			try {
				while(!r.ready())
					Thread.sleep(1);
				line = r.readLine();
				System.err.println("Request line: (" + line + ")");
				if(line.length() == 0)
					break;
				if(request == null)
					request = line;
			}

			catch (Exception e){
				System.err.println("Request error: " + e);
				break;
			}

		}

		String[] requestedSplit = request.split(" ");
		File requestedFile;

		//process the request
		//check if it has GET requests
		if(requestedSplit[0].equals("GET")){
			//get path
			requestedFile = new File((requestedSplit[1].substring(1)));

			//check if file exists
			if(!requestedFile.isDirectory() && requestedFile.exists()){
				return requestedFile;
			}
			else if(requestedSplit[1].endsWith("/")){
				landingPage = true;
			}

		}

		return null;

	}

	/**
	 * Write the HTTP header lines to the client network connection.
	 *
	 * @param os
	 *          is the OutputStream object to write to
	 * @param contentType
	 *          is the string MIME content type (e.g. "text/html")
	 **/
	private void writeHTTPHeader(OutputStream os, String contentType, File request) throws Exception
	{
		Date d = new Date();
		DateFormat df = DateFormat.getDateTimeInstance();
		df.setTimeZone(TimeZone.getTimeZone("GMT"));
		if(request != null || landingPage)
			os.write("HTTP/1.1 200 OK\n".getBytes());
		else
			os.write("HTTP/1.1 404 Not Found!\n".getBytes());
		os.write("Date: ".getBytes());
		os.write((df.format(d)).getBytes());
		os.write("\n".getBytes());
		os.write("Server: Jason's very own server\n".getBytes());
		os.write("Connection: close\n".getBytes());
		os.write("Content-Type: ".getBytes());
		os.write(contentType.getBytes());
		os.write("\n\n".getBytes()); // HTTP header ends with 2 newlines
		return;
	}

	/**
	 * checks the content of incoming file and returns correct ending
	 *
	 * @param request
	 *          requested file
	 * @return
	 *          String of typeOfContent
	 **/
	 private String checktypeOfContent(File request){

		 if(request == null){
			 return "text/html";
		 }

		 HashMap<String, String> extensions = new HashMap<String, String>();
		 extensions.put("jpeg", "image/jpeg");
		 extensions.put("jpg", "image/jpeg");
		 extensions.put("png", "image/png");
		 extensions.put("gif", "image/gif");

		 String name = request.getName();
		 name = name.substring((name.lastIndexOf(".") + 1));

		 return extensions.getOrDefault(name, "text/html");

	 }

	/**
	 * Write the data content to the client network connection. This MUST be done after the HTTP
	 * header has been written out.
	 *
	 * @param os
	 *          is the OutputStream object to write to
	 **/
	private void writeContent(OutputStream os, File requested, String typeOfContent) throws Exception {
		if(requested == null || !requested.exists()){
			os.write("<h3>404 Not Found</h3>\n".getBytes());
		}
		else if(landingPage){
			os.write("<html><head></head><body>\n".getBytes());
			os.write("<h3>My web server works!</h3>\n".getBytes());
			os.write("</body></html>\n".getBytes());
		}
		else if(typeOfContent.equals("text/html")){
			os.write("<html><body>\n".getBytes());
			Scanner scan = new Scanner(requested);
			while(scan.hasNextLine()){
				String in = scan.nextLine();
				if(in.contains("<cs371date>")){
					LocalDate date = LocalDate.now();
					in = in.replaceAll("<cs371date>", date.toString());
				}
				if (in.contains("<cs371server>")) {
					in = in.replaceAll("<cs371server>", "Jason's Server");
				}
				os.write(in.getBytes());
			}
			scan.close();
			os.write("</body></html>\n".getBytes());
		}
		//we know we have some type of image if we get to this piont
		byte[] imageBytes = Files.readAllBytes(requested.toPath());
		os.write(imageBytes);

	}

} // end class
