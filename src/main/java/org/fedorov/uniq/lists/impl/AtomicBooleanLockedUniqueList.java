package org.fedorov.uniq.lists.impl;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AtomicBooleanLockedUniqueList<T> extends SimpleNonUniqueList<T> {

    private final AtomicBoolean islocked = new AtomicBoolean(false);

    public AtomicBooleanLockedUniqueList()
    {
        super();
    }

    public AtomicBooleanLockedUniqueList(List<T> list){
        super(list);
    }

    @Override
    public  boolean add(T e){
        boolean added = false;
        while ( !added && !list.contains(e)) {
            if( islocked.compareAndSet(false, true) ) {
                try{
                    if( !list.contains(e) ){
                        added = list.add(e);
                    }
                }finally{
                    islocked.set(false);
                } 
            }else {
                Thread.yield();
            }
        }
        
        return added;
    }
}
