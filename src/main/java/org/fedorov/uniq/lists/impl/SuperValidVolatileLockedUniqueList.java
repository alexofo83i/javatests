package org.fedorov.uniq.lists.impl;

import java.util.List;

public class SuperValidVolatileLockedUniqueList<T> extends SimpleNonUniqueList<T> {

    private volatile boolean locked = false;

    public SuperValidVolatileLockedUniqueList() {
        super();
    }

    public SuperValidVolatileLockedUniqueList(List<T> list) {
        super(list);
    }

    @Override
    public boolean add(T e) {
        // Просто пытаемся добавить элемент, блокировка теперь полностью внутри lock()
        if (!list.contains(e)) {
            lock(); // Блокируемся с адаптивным ожиданием
            try {
                // Двойная проверка после получения блокировки
                if (!list.contains(e)) {
                    return list.add(e);
                }
                return false;
            } finally {
                unlock();
            }
        }
        return false;
    }

    private void lock() {
        int spins = 0;
        while (!tryLock()) {
            if (spins++ < 100) {
                // Первые 100 раз просто крутимся - очень быстро
                continue;
            } else if (spins < 1000) {
                // Потом начинаем делать паузы
                Thread.onSpinWait(); // Специальная инструкция CPU (Java 9+)
            } else {
                // Если долго не получается - засыпаем
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // Восстанавливаем статус прерывания и продолжаем попытки
                }
            }
        }
    }

    private boolean tryLock() {
        if (locked) {
            return false;
        }

        synchronized (list) {
            if (!locked) {
                locked = true;
                return true;
            }
            return false;
        }
    }

    private void unlock() {
        locked = false;
    }
}