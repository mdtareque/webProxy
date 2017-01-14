Advanced Computer Networks - WebProxy HTTP 1.0 - Java - Few Cache Control Headers Implemented

Implemented in : Java 1.7

Command to run:
$ cd src
$ java proxy 9090
# in separate telnet session, give the GET and HEAD commands
e.g.
GET http://www.google.co.in/ HTTP/1.1
GET http://www.google.com/ HTTP/1.1
GET http://intranet.iiit.ac.in/ HTTP/1.1 
GET http://intranet.iiit.ac.in/ HTTP/1.0 # version different
GET http://websites.web.com/ HTTP/1.1
GET http://www.networkpolo.com/wp-content/uploads/2016/04/What-is-Web-Proxy.jpg HTTP/1.1
GET http://i1.wp.com/digitalpk.com/wp-content/uploads/2014/08/web-proxy-server.jpg HTTP/1.1
GET http://www.jmarshall.com/easy/http/ HTTP/1.1
GET http://www.geeksforgeeks.org/output-python-program-set-1/ HTTP/1.1


 > telnet localhost 1234
Trying 127.0.0.1...
Connected to localhost.
Escape character is '^]'.
PUT http://www.google.com HTTP/1.1
HTTP/1.1 501 Not Implemented
Allow: GET, HEAD
Cache-Control: no-cache, must-revalidate
Connection: close
Connection closed by foreign host.


Folders:
 src/ contains the source code and compiled class files

Task Done:
1. GET and HEAD request
2. Multithreading
3. Caching done with local disk storage
4. Sends '501 Not Implemented' response for any request other than GET and HEAD

