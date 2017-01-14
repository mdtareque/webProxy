import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;


class Config {
    public static final String HTTP_VERSION = "HTTP/1.1";
    public static final String PROXY_LOG = "proxy.log";
    public static final String PROXY_CACHE_FILENAME  = "proxyCache";
}


public class proxy0 {

    static {
        makeCacheFolder();
    }

    public static void main(String[] args) throws Exception {

        ServerSocket listener = null;
        try {
            if (args.length != 1) // expect port
                throw new IllegalArgumentException("insufficient arguments");

            int port = Integer.parseInt(args[0]);
            System.out.println("The proxy server is running on port " + port);
            listener = new ServerSocket(port);


            while (true) {
                new HTTPProxy(listener.accept()).start();
            }

        } catch (IllegalArgumentException e) {
            System.err.println("Usage: java proxy0 <port>");
        } finally {
            if (listener != null) {
                listener.close();
            }
        }
    }

    public static void makeCacheFolder() {
        File cacheFolder = new File(Config.PROXY_CACHE_FILENAME);
        if (cacheFolder.exists()) {
            System.out.println("Cache folder already exists : " + Config.PROXY_CACHE_FILENAME);
            return;
        }
        cacheFolder.mkdir();
        System.out.println("Cache folder created : " + Config.PROXY_CACHE_FILENAME);
    }
}


class HTTPProxy extends Thread {
    private Socket clientSocket;
    FileWriter fstream;
    BufferedWriter logOut;

    String dateTime = new SimpleDateFormat("MMM dd yyyy HH:mm:ss").format(Calendar.getInstance().getTime());

    public HTTPProxy(Socket socket) {
        this.clientSocket = socket;
        try {
            fstream = new FileWriter(Config.PROXY_LOG, true);
            logOut = new BufferedWriter(fstream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            BufferedReader clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String input;

            input = clientIn.readLine();
            //System.out.println(input);
            //we only are concerned with valid GET requests
            boolean getReq = false;
            boolean headReq = false;
            if (input == null) {
                clientSocket.close();
                return;
            }
            if(input.contains("GET")) getReq = true;
            if(input.contains("HEAD"))  headReq = true;
            if(getReq || headReq) {
//                System.out.println(input);

                //GET url HTTPVERSION
                //we care about the second spot after splitting
                String req = input.split(" ")[0];
                URL url = new URL(input.split(" ")[1]);
                String host = url.getHost();
                String file = url.getFile();

                //file names can't contain certain symbols, so get rid of all symbols
                String textFileName = Config.PROXY_CACHE_FILENAME +"/" + req + (host + "_" + file).replaceAll("[^a-zA-Z0-9.-]", "") + ".txt";

                //first check if we have cached this request
                if (!cacheFileExists(textFileName)) {
                    Socket server = null;
                    // if iiit internal website then hit directly, else go via proxy
                    if (host.matches(".+\\.iiit.*")) {
                        server = new Socket(host, 80);
                        //Socket server = new Socket("intranet.iiit.ac.in", 80);
                        //Socket server = new Socket("google.co.in", 80);
                    } else {
                        server = new Socket(host, 8080);
                        //server = new Socket("proxy.iiit.ac.in", 8080);
                    }

                    //sends the GET request to the server
                    createGetRequest(host, input.split(" ")[1], server);

                    //out stream to send data back to client
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    //in stream to get data from proxy.iiit.ac.in
                    InputStream serverInput = server.getInputStream();

                    boolean shouldCache = false;
                    StringBuilder bufCache = new StringBuilder("");
                    //handle image files differently
                    if (!isImageorDocFile(file)) {

                        PrintWriter out = new PrintWriter(clientOutput, true);
                        BufferedReader serverInputReader = new BufferedReader(new InputStreamReader(serverInput));

                        int responseLength = 0;
                        String line;
                        while ((line = serverInputReader.readLine()) != null) {
                            //grab the length
                            if (line.startsWith("Content-Length"))
                                responseLength = Integer.parseInt(line.split(" ")[1]);

                            if (line.startsWith("Cache-Control")) {
                                String value = line.split(" ")[1];
                                System.out.println("Cache-Control header found " +  line);
                                if (!value.contains("private") && !value.contains("no-store") && !value.contains("no-cache"))
                                    shouldCache = true;
                            }


                            bufCache.append(line + "\n");
                            //textOut.write(line + "\n");
                            out.println(line);
                            if(line.length()==0 && headReq) {
                                System.out.println("HEAD requested");
                                break;
                            }
                        }
                        if (shouldCache) {
                            try {
                                FileWriter fstream = new FileWriter(textFileName);
                                BufferedWriter textOut = new BufferedWriter(fstream);
                                System.out.println("File is cached");
                                textOut.write(bufCache.toString());
                                textOut.close();
                            } catch (FileNotFoundException e) {

                            }
                        } else {
                            System.out.println("Not caching as control headers or private|no-store|no-cache found");
                        }

                        logRequest(clientSocket.getInetAddress().getHostAddress(), url.toString(), responseLength);

                        serverInputReader.close();
                    } else {
                        int count;
                        //create some temporary storage
                        byte[] buffer = new byte[8192];
                        //transfer bytes from input to browser
                        while ((count = serverInput.read(buffer)) > 0) {

                            clientOutput.write(buffer, 0, count);
                        }
                        System.out.println("File is not cached");

                    }
                    server.close();
                    clientSocket.close();
                } else {
                    System.out.println("Serving from cache");

                    PrintWriter clientOut = new PrintWriter(clientSocket.getOutputStream(), true);

                    BufferedReader reader = new BufferedReader(new FileReader(textFileName));
                    String currentLine;

                    //copy the cache to the browser line by line
                    while ((currentLine = reader.readLine()) != null) {
                        clientOut.println(currentLine);
                    }

                    reader.close();
                    clientSocket.close();
                }
            } else  {
                // send not implemented

                OutputStream clientOutput = clientSocket.getOutputStream();
                PrintWriter out = new PrintWriter(clientOutput, true);
                sendLine(out, Config.HTTP_VERSION + " 501 Not Implemented");
                sendLine(out, "Allow", "GET, HEAD");
                sendLine(out,"Cache-Control", "no-cache, must-revalidate");
                sendLine(out,"Connection", "close");

                out.close();
                clientSocket.close();
            }
        } catch (IOException e) {
            //something went wrong
            e.printStackTrace();
        }
    }


    public void sendLine(PrintWriter out, String s) throws IOException {
        out.write(s + "\r\n");
    }


    public void sendLine(PrintWriter out, String header, String s) throws IOException {
        out.write( header + ": " + s + "\r\n");
    }


    private void createGetRequest(String host, String url, Socket server) throws IOException {
        PrintWriter pw = new PrintWriter(server.getOutputStream());

        //following the protocol
//        pw.println("GET " + file + " HTTP/1.0");
        pw.println("GET " + "/" + " HTTP/1.0");
        //pw.println("GET " + url + " HTTP/1.0");
//        pw.println(String.format("Host: %s", host));
        pw.println("");
        pw.flush();
    }

    private boolean cacheFileExists(String fileName) {
        File textFile = new File(fileName);
        return textFile.exists();
    }

    private boolean isImageorDocFile(String fileName) {
        String lowered = fileName.toLowerCase();
        //check if file name contains common image file extensions
        return lowered.contains(".jpg") || lowered.contains("gif")
                || lowered.contains(".bmp") || lowered.contains(".png")
                || lowered.contains(".ico") || lowered.contains(".pdf");
    }

    private void logRequest(String ip, String url, int responseLength) throws IOException {
        String logLine = String.format("%s\t%s\t%s\t%s", dateTime, ip, url, responseLength);

        logOut.append(logLine + "\n");
        System.out.println(logLine);
    }

    @Override
    protected void finalize() throws Throwable {
        logOut.close();
    }
}
