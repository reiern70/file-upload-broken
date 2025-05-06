This is amven problem that illustrates how wicket file file upload is broken for tomcat 10.0.46 and tomcat 11.0.6

Wicket uses common fileupload2 in order to parse the quest. The problem seesm to be if beroe this at some point a parameter is exytracted from request the tomcat will parse multipart request and this logic will find an exasuted stream (and return no files). 
This can be cheked by putting a breakpint at org.apache.coyote.http11.filters.IdentityInputFilter at line 94. This does not depends on fileupload2. I have also included a modification of wicket machinery that uses tomcat multipart 
machiney and the same problem arises (see TomcatMultipartServletWebRequestImpl).

I can try to go backwards on time and see when this was "broken".

Another alternative would be to allow to plug in some "listener" somehow so that wicket could use this for upload progress. But I guess this makes code non stnadard and dependent on tomcat.

