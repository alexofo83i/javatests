package org.fedorov.uniq.lists.impl;

import java.util.List;

public class NonValidVolatileLockedUniqueList<T> extends SimpleNonUniqueList<T> {

    private volatile boolean islocked = false;

    public NonValidVolatileLockedUniqueList()
    {
        super();
    }

    public NonValidVolatileLockedUniqueList(List<T> list){
        super(list);
    }

    @Override
    public  boolean add(T e){
        if( !islocked ) {
            synchronized(this){
                if( !islocked ){
                   islocked = true;
                   try{
                        if( !list.contains(e) ){
                            list.add(e);
                            return true;
                        }
                   }finally{
                        islocked = false;
                   } 
                }
            }
        }
        
        return false;
    }
}
