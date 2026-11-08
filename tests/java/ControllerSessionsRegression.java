package com.happy.cinemaframe;
import java.util.*;
public class ControllerSessionsRegression {
 public static void main(String[] args){
  Set<String> sessions=new HashSet<>(Arrays.asList("phone-a","phone-b","ha"));
  String token=ControllerSessions.authenticated("other=1;cf_session=phone-a; theme=dark",sessions);
  if(!token.equals("phone-a"))throw new AssertionError("cookie parsing");
  for(int i=0;i<8;i++)if(!ControllerSessions.pair(sessions,token).equals("phone-a"))throw new AssertionError("re-pair rotated existing session");
  if(sessions.size()!=3)throw new AssertionError("re-pair inflated count");
  if(!ControllerSessions.authenticated("cf_session=unknown",sessions).isEmpty())throw new AssertionError("unknown cookie accepted");
  String fresh=ControllerSessions.pair(sessions,"");if(sessions.size()!=4||!sessions.contains(fresh)||!sessions.contains("phone-b"))throw new AssertionError("new pairing broke existing sessions");
  System.out.println("PASS ControllerSessionsRegression (synthetic): repeated pairing reuses authentication; new device preserves prior sessions; unknown cookie rejected");
 }
}
