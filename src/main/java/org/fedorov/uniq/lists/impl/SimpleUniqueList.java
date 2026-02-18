package org.fedorov.uniq.lists.impl;

import java.util.List;

public class SimpleUniqueList<T> extends SimpleNonUniqueList<T> {

    public SimpleUniqueList()
    {
        super();
    }

    public SimpleUniqueList(List<T> list){
        super(list);
    }

    @Override
    public boolean add(T e){
        if( !list.contains(e) ){
            list.add(e);
            return true;
        }
        return false;
    }
}
