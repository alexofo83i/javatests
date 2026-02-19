package org.fedorov.uniq.lists.impl;

import java.util.List;

public class SynchronizedSectionUniqueList<T> extends SimpleNonUniqueList<T> {


    public SynchronizedSectionUniqueList()
    {
        super();
    }

    public SynchronizedSectionUniqueList(List<T> list){
        super(list);
    }

    @Override
    public boolean add(T e){
        synchronized (list) {
            if( !list.contains(e) ){
                list.add(e);
                return true;
            }
            return false;
        }
    }
}
