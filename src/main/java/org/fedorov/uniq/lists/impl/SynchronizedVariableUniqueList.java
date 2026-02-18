package org.fedorov.uniq.lists.impl;

import java.util.List;

public class SynchronizedVariableUniqueList<T> extends SimpleNonUniqueList<T> {

    public SynchronizedVariableUniqueList()
    {
        super();
    }

    public SynchronizedVariableUniqueList(List<T> list){
        super(list);
    }

    @Override
    public  boolean add(T e){
        synchronized(list){
            if( !list.contains(e) ){
                list.add(e);
                return true;
            }
        }
        return false;
    }
}
