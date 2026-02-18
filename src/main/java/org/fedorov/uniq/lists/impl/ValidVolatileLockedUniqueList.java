package org.fedorov.uniq.lists.impl;

import java.util.List;

public class ValidVolatileLockedUniqueList<T> extends SimpleNonUniqueList<T> {

    private volatile boolean islocked = false;

    public ValidVolatileLockedUniqueList()
    {
        super();
    }

    public ValidVolatileLockedUniqueList(List<T> list){
        super(list);
    }

    @Override
    public  boolean add(T e){
        boolean added = false;
        while ( !added && !list.contains(e)){
            if( tryLock()){
                try {
                    if (!list.contains(e)) {
                        added = list.add(e);
                    }
                } finally {
                    unlock();
                }
            }else {
                Thread.yield();
            }
        }
        return added;   
    }

    private boolean tryLock(){
        if (islocked) {
            return false;
        }

        synchronized (list) {
            if ( !islocked ) {
                islocked = true;
                return true;
            }
            return false;
        }
    }

    private void unlock(){
         islocked = false;
    }
}
