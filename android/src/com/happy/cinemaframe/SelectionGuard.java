package com.happy.cinemaframe;

import java.util.HashSet;
import java.util.Set;

/** Keep both the visible selection and last audible film out of the next draw. */
final class SelectionGuard {
    static Set<String> excluded(String selected,String played,String current){
        Set<String> ids=new HashSet<>();
        for(String id:new String[]{selected,played,current})if(id!=null&&!id.isEmpty())ids.add(id);
        return ids;
    }
}
