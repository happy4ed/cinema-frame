package com.happy.cinemaframe;
/** Conservative local budget, not a claimed Spotify service quota. */
final class SearchBudget {
 static final long INTERVAL=15000,WINDOW=3600000;static final int LIMIT=60;
 static long allowedAt(long now,long serverRetry,long next,long windowStart,int count){return Math.max(serverRetry,Math.max(next,count>=LIMIT&&now<windowStart+WINDOW?windowStart+WINDOW:0));}
}
