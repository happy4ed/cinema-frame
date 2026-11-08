package com.happy.cinemaframe;
import java.util.*;
public class PlaybackPlanRegression {
 public static void main(String[] args){
  List<String> plan=new ArrayList<>();for(int m=0;m<5;m++)for(int t=0;t<3;t++)plan.add(m+":"+t);
  Set<String> played=new HashSet<>();String previous="";int cursor=0;
  while(cursor<plan.size()){final String last=previous;int i=PlaybackPlan.next(plan,cursor,x->!x.substring(0,1).equals(last),x->x.substring(0,1));if(i<0)break;String x=plan.get(i);if(!played.add(x))throw new AssertionError("repeated before all tracks");previous=x.substring(0,1);cursor=i+1;}
  if(played.size()!=15)throw new AssertionError("expected all 15 tracks, got "+played.size());
  if(PlaybackPlan.expand(false,true,101,100))throw new AssertionError("must honor Retry-After");
  if(!PlaybackPlan.expand(false,true,100,100))throw new AssertionError("must expand cache after cooldown");
  if(PlaybackPlan.expand(true,true,0,100))throw new AssertionError("explicit cached-only must stay cached");
  if(PlaybackPlan.expand(false,false,0,100))throw new AssertionError("fresh plan is not a cache bridge");
  System.out.println("PASS PlaybackPlanRegression: 15/15 unique tracks, no consecutive film; cooldown and explicit cache policy (synthetic data)");
 }
}
