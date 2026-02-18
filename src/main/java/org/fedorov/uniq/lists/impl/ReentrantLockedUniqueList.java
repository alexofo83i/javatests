package org.fedorov.uniq.lists.impl;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockedUniqueList<T> extends SimpleNonUniqueList<T> {

    private final ReentrantLock lock = new ReentrantLock();

    public ReentrantLockedUniqueList()
    {
        super();
    }

    public ReentrantLockedUniqueList(List<T> list){
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
        return lock.tryLock();
    }

    private void unlock(){
         lock.unlock();
    }
}
