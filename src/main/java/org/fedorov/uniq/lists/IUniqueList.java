package org.fedorov.uniq.lists;

import java.util.List;

public interface IUniqueList<T> {
    public boolean add(T e);
    public int size();
     public T get(int index);
}