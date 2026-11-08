package com.happy.cinemaremote;
public class ReleaseVersionRegression {
 public static void main(String[] args){
  if(!ReleaseVersion.newer("v0.5.1","0.3-test"))throw new AssertionError("old phone must update");
  if(ReleaseVersion.newer("v0.5.1","0.5.1-test"))throw new AssertionError("installed build must not redownload");
  if(ReleaseVersion.newer("v0.5-test-20261108","0.5.1-test"))throw new AssertionError("must not downgrade");
  if(!ReleaseVersion.newer("v0.5.2","0.5.1-test"))throw new AssertionError("next patch must update");
  if(!ReleaseVersion.newer("v0.10.0","0.9.9"))throw new AssertionError("numeric comparison");
  try{ReleaseVersion.newer("unknown","0.5.1-test");throw new AssertionError("invalid tag accepted");}catch(IllegalArgumentException expected){}
  System.out.println("PASS ReleaseVersionRegression (fixtures): upgrade, same version, downgrade, next patch, numeric comparison, invalid tag");
 }
}
