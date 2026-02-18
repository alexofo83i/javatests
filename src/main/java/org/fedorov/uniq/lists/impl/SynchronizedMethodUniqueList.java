package org.fedorov.uniq.lists.impl;

import java.util.List;

public class SynchronizedMethodUniqueList<T> extends SimpleNonUniqueList<T> {


    public SynchronizedMethodUniqueList()
    {
        super();
    }

    public SynchronizedMethodUniqueList(List<T> list){
        super(list);
    }

    @Override
    public synchronized boolean add(T e){
        if( !list.contains(e) ){
            list.add(e);
            return true;
        }
        return false;
    }
}
