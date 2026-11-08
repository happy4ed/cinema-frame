package com.happy.cinemaframe;
import java.util.*;
/** Authentication sessions are not physical device identities. */
final class ControllerSessions {
 static String authenticated(String cookie,Set<String> sessions){
  for(String part:cookie.split(";")){String value=part.trim();if(value.startsWith("cf_session=")){String token=value.substring(11);if(sessions.contains(token))return token;}}
  return "";
 }
 static String pair(Set<String> sessions,String authenticated){
  if(sessions.contains(authenticated))return authenticated;
  String token=UUID.randomUUID().toString();sessions.add(token);return token;
 }
}
