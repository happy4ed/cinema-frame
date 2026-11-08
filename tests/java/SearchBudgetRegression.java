package com.happy.cinemaframe;
public class SearchBudgetRegression {
 public static void main(String[] args){
  long now=100000;
  if(SearchBudget.allowedAt(now,now+90000,0,now,0)!=now+90000)throw new AssertionError("Retry-After shortened");
  if(SearchBudget.allowedAt(now,0,now+15000,now,1)!=now+15000)throw new AssertionError("spacing lost");
  if(SearchBudget.allowedAt(now,0,0,now,60)!=now+3600000)throw new AssertionError("hour quota lost");
  if(SearchBudget.allowedAt(now+3600000,0,0,now,60)>now+3600000)throw new AssertionError("window never resets");
  if(SearchBudget.allowedAt(now,now+7200000,now+15000,now,60)!=now+7200000)throw new AssertionError("local limit overrides server");
  System.out.println("PASS SearchBudgetRegression (simulated clock): 15-second interval, 60/hour, window reset, server Retry-After precedence");
 }
}
