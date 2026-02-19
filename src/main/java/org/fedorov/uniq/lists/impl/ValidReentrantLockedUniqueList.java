package org.fedorov.uniq.lists.impl;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class ValidReentrantLockedUniqueList<T> extends SimpleNonUniqueList<T> {

    private final ReentrantLock lock = new ReentrantLock();

    public ValidReentrantLockedUniqueList()
    {
        super();
    }

    public ValidReentrantLockedUniqueList(List<T> list){
        super(list);
    }

    @Override
    public  boolean add(T e){
        boolean added = false;
        lock();
        try {
            if (!list.contains(e)) {
                added = list.add(e);
            }
        } finally {
            unlock();
        }
        return added;   
    }

    private void lock(){
        lock.lock();
    }

    private void unlock(){
         lock.unlock();
    }
}
