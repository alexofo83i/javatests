package org.fedorov.uniq.lists;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.fedorov.uniq.lists.impl.AtomicBooleanLockedUniqueList;
import org.fedorov.uniq.lists.impl.NonValidVolatileLockedUniqueList;
import org.fedorov.uniq.lists.impl.ReentrantLockedUniqueList;
import org.fedorov.uniq.lists.impl.SimpleNonUniqueList;
import org.fedorov.uniq.lists.impl.SimpleUniqueList;
import org.fedorov.uniq.lists.impl.SuperValidVolatileLockedUniqueList;
import org.fedorov.uniq.lists.impl.SynchronizedMethodUniqueList;
import org.fedorov.uniq.lists.impl.ValidReentrantLockedUniqueList;
import org.fedorov.uniq.lists.impl.ValidVolatileLockedUniqueList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class IUniqueListJUnitTest {
    
    public static Stream<Arguments> provideTestData(){
        return Stream.of(
            arguments(SimpleNonUniqueList.class, false, 3, List.of("one", "two", "one"))
          // , arguments(SimpleUniqueList.class, false, 2, List.of("one", "two", "one"))
          , arguments(SynchronizedMethodUniqueList.class, true, 2, List.of("one", "two", "one"))
          , arguments(NonValidVolatileLockedUniqueList.class, true, 2, List.of("one", "two", "one"))
          , arguments(ValidVolatileLockedUniqueList.class, true, 2, List.of("one", "two", "one"))
          , arguments(SuperValidVolatileLockedUniqueList.class, true, 2, List.of("one", "two", "one"))
          , arguments(AtomicBooleanLockedUniqueList.class, true, 2, List.of("one", "two", "one"))
          , arguments( ReentrantLockedUniqueList.class, true, 2, List.of("one", "two", "one"))
          , arguments( ValidReentrantLockedUniqueList.class, true, 2, List.of("one", "two", "one"))
        );
    }

    @ParameterizedTest
    @MethodSource("provideTestData")
    <T> void testAddTwoUniqueElementsIntoListInSingleThread(Class<T> clazz, boolean expectedSuccess, int  expectedResult, List<T> elements) throws Exception {
        @SuppressWarnings("unchecked")
        IUniqueList<T> list = (IUniqueList<T>) clazz.getDeclaredConstructor().newInstance();
        for( T element : elements ) {
            list.add(element);
        }
        assertEquals(expectedResult, list.size(), String.format("Size is not valid for implementation %s", clazz.getName()));
    }


    @ParameterizedTest
    @MethodSource("provideTestData")
    <T> void testAddTwoUniqueElementsIntoListInSingleThreadUsingSlowAdd(Class<T> clazz, boolean expectedSuccess, int  expectedResult, List<T> elements) throws Exception {
        
        List<T> internalList = new ArrayList<>(){
            @Override
            public boolean add(T e){
                try{
                    Thread.sleep(5000);
                }catch (InterruptedException ex){
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ex);
                }
                return super.add(e);
            }
        };
        
        @SuppressWarnings("unchecked")
        IUniqueList<T> list = (IUniqueList<T>) clazz.getDeclaredConstructor(List.class).newInstance(internalList);
        for( T element : elements ) {
            list.add(element);
        }
        assertEquals(expectedResult, list.size(), String.format("Size is not valid for implementation %s", clazz.getName()));
    }


    @ParameterizedTest
    @MethodSource("provideTestData")
    <T> void testAddTwoUniqueElementsIntoListInMultipleThreads(Class<T> clazz, boolean expectedSuccess, int  expectedResult, List<T> elements) throws Exception {
        @SuppressWarnings("unchecked")
        IUniqueList<T> list = (IUniqueList<T>) clazz.getDeclaredConstructor().newInstance();
        
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        
        List<Exception> exceptions = new CopyOnWriteArrayList<>();
        
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for ( int j = 0; j < 100; j++) {
                        for (T element : elements ) {
                            Thread.sleep(1);
                            list.add(element);
                            Thread.sleep(1);
                        }
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        finishLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(exceptions.isEmpty(), 
                    String.format("Some exceptions has been occurred during the test: %s", 
                        exceptions.stream()
                            .map(e -> String.format("%s: %s%n%s", 
                                e.getClass().getSimpleName(), 
                                e.getMessage(), 
                                java.util.Arrays.toString(e.getStackTrace())))
                            .collect(Collectors.joining("\n---\n"))));

        if ( expectedSuccess )
            assertEquals(expectedResult, list.size(), String.format("Size is not valid for implementation %s", clazz.getName()));
        else 
            assertTrue(expectedResult != list.size(), String.format("Size is not valid for implementation %s: actual %d, but expected %d", clazz.getName(), list.size(), expectedResult));
    }

    public static Stream<Arguments> provideTestData2(){
        return Stream.of(
            arguments(SimpleNonUniqueList.class, false)
        //   , arguments(SimpleUniqueList.class, false)
          , arguments(SynchronizedMethodUniqueList.class, true)
          , arguments(NonValidVolatileLockedUniqueList.class, false)
          , arguments(ValidVolatileLockedUniqueList.class, true)
          , arguments(SuperValidVolatileLockedUniqueList.class, true)
          , arguments(AtomicBooleanLockedUniqueList.class, true)
          , arguments(ReentrantLockedUniqueList.class, true)
          , arguments(ValidReentrantLockedUniqueList.class, true)
        );
    }

    @ParameterizedTest
    @MethodSource("provideTestData2")
    <T> void testAddElementsIntoListInMultipleThreadsUsingSlowAdd(Class<IUniqueList<Integer>> clazz, boolean expectedSuccess) throws Exception {
        List<Integer> internalList = new ArrayList<>(){
            @Override
            public boolean add(Integer e){
                try{
                    Thread.sleep(100);
                }catch (InterruptedException ex){
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ex);
                }
                return super.add(e);
            }
        };
        
        @SuppressWarnings("unchecked")
        final IUniqueList<Integer> list = (IUniqueList<Integer>) clazz.getDeclaredConstructor(List.class).newInstance(internalList);
        
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        
        List<Exception> exceptions = new CopyOnWriteArrayList<>();
        
        final AtomicInteger atom = new AtomicInteger(0);
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                int threadid = atom.incrementAndGet();
                try {
                    startLatch.await();
                    for ( int j = 0; j < 10; j++) {
                        Thread.sleep(1);
                        list.add(threadid*10+j);
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        finishLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(exceptions.isEmpty(), 
                    String.format("Some exceptions has been occurred during the test: %s", 
                        exceptions.stream()
                            .map(e -> String.format("%s: %s%n%s", 
                                e.getClass().getSimpleName(), 
                                e.getMessage(), 
                                java.util.Arrays.toString(e.getStackTrace())))
                            .collect(Collectors.joining("\n---\n"))));

        if ( expectedSuccess )
            assertEquals(100, list.size(), String.format("Size is not valid for implementation %s", clazz.getName()));
        else 
            assertTrue(100 != list.size(), String.format("Size is not valid for implementation %s: actual %d, but expected %d", clazz.getName(), list.size(), 100));
    }
}