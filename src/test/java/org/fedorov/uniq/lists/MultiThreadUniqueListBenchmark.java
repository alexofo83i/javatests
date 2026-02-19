package org.fedorov.uniq.lists;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.fedorov.uniq.lists.impl.AtomicBooleanLockedUniqueList;
import org.fedorov.uniq.lists.impl.ReentrantLockedUniqueList;
import org.fedorov.uniq.lists.impl.SynchronizedMethodUniqueList;
import org.fedorov.uniq.lists.impl.SynchronizedSectionUniqueList;
import org.fedorov.uniq.lists.impl.ValidVolatileLockedUniqueList;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(value = 1)
public class MultiThreadUniqueListBenchmark {

   public enum ListImplementation {
        SYNCHRONIZED_METHOD(SynchronizedMethodUniqueList.class.getName()),
        SYNCHRONIZED_SECTION(SynchronizedSectionUniqueList.class.getName()),
        ATOMIC_BOOLEAN(AtomicBooleanLockedUniqueList.class.getName()),
        VALID_VOLATILE(ValidVolatileLockedUniqueList.class.getName()),
        REENTRANT_LOCK(ReentrantLockedUniqueList.class.getName());
        
        private final String className;
        
        ListImplementation(String className) {
            this.className = className;
        }
        
        public String getClassName() {
            return className;
        }
    }

    @Param
    private ListImplementation implementationName;
    
    @Param({"10"})
    private int operationsPerThread;

    private IUniqueList<Integer> list;
    private ThreadLocal<Integer> threadId = ThreadLocal.withInitial(() -> 0);
    private AtomicInteger idGenerator = new AtomicInteger(0);
    private ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
    
    @Setup(Level.Iteration)
    public void setupIteration() throws Exception {
        List<Integer> internalList = new ArrayList<>() {
            @Override
            public boolean add(Integer e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ex);
                }
                return super.add(e);
            }
        };
        
        Class<?> clazz = Class.forName(implementationName.getClassName());
        Constructor<?> constructor = clazz.getDeclaredConstructor(List.class);
        
        @SuppressWarnings("unchecked")
        IUniqueList<Integer> instance = (IUniqueList<Integer>) constructor.newInstance(internalList);
        this.list = instance;
        
        idGenerator.set(0);
        errors.clear();
    }
    
    @TearDown(Level.Iteration)
    public void tearDownIteration(Blackhole blackhole) {
        if (!errors.isEmpty()) {
            blackhole.consume("Errors: " + errors.size());
        }
        
        // Проверяем результат
        int actualSize = list.size();
        int expectedSize = operationsPerThread * getThreadCountFromContext();
        blackhole.consume(actualSize);
        blackhole.consume(expectedSize);
        
        // Проверка уникальности
        java.util.HashSet<Integer> set = new java.util.HashSet<>();
        for (int i = 0; i < actualSize; i++) {
            if (!set.add(list.get(i))) {
                blackhole.consume("Duplicate found at index: " + i);
            }
        }
    }
    
    private int getThreadCountFromContext() {
        // Пытаемся получить количество потоков из контекста JMH
        try {
            String threads = System.getProperty("jmh.threads", "1");
            return Integer.parseInt(threads);
        } catch (Exception e) {
            return 1;
        }
    }
    
    @Benchmark
    @Threads(2)
    public void testWith2Threads(Blackhole blackhole) {
        runBenchmark(blackhole);
    }
    
    @Benchmark
    @Threads(4)
    public void testWith4Threads(Blackhole blackhole) {
        runBenchmark(blackhole);
    }
    
    @Benchmark
    @Threads(8)
    public void testWith8Threads(Blackhole blackhole) {
        runBenchmark(blackhole);
    }
    
    @Benchmark
    @Threads(16)
    public void testWith16Threads(Blackhole blackhole) {
        runBenchmark(blackhole);
    }
    
    private void runBenchmark(Blackhole blackhole) {
        int id = threadId.get();
        if (id == 0) {
            id = idGenerator.incrementAndGet();
            threadId.set(id);
        }
        
        try {
            for (int j = 0; j < operationsPerThread; j++) {
                // Микро-паузы
                if (j % 3 == 0) {
                    Blackhole.consumeCPU(100);
                }
                
                int value = id * operationsPerThread + j;
                boolean added = list.add(value);
                blackhole.consume(added);
                
                if (j % 5 == 0) {
                    Blackhole.consumeCPU(50);
                }
            }
        } catch (Exception e) {
            errors.add(e);
        }
    }
    
    @Test
    public void testMultiThreadUniqueListBenchmark() throws Exception {

        // Создаем директорию для результатов перед запуском бенчмарка
        java.nio.file.Path resultsPath = java.nio.file.Paths.get("./results");
        if (!java.nio.file.Files.exists(resultsPath)) {
            java.nio.file.Files.createDirectories(resultsPath);
            System.out.println("Created results directory: " + resultsPath.toAbsolutePath());
        }
        
        // Также создаем другие необходимые директории
        java.nio.file.Path dumpsPath = java.nio.file.Paths.get("./dumps");
        if (!java.nio.file.Files.exists(dumpsPath)) {
            java.nio.file.Files.createDirectories(dumpsPath);
        }
        
        java.nio.file.Path asyncPath = java.nio.file.Paths.get("./async");
        if (!java.nio.file.Files.exists(asyncPath)) {
            java.nio.file.Files.createDirectories(asyncPath);
        }
        
        java.nio.file.Path jfrPath = java.nio.file.Paths.get("./jfr");
        if (!java.nio.file.Files.exists(jfrPath)) {
            java.nio.file.Files.createDirectories(jfrPath);
        }

        ChainedOptionsBuilder optBuilder = new OptionsBuilder()
                .include(MultiThreadUniqueListBenchmark.class.getSimpleName())
                .resultFormat(ResultFormatType.JSON)
                .jvmArgs(
                    "-Xms4G", "-Xmx4G",
                    "-XX:+UseG1GC",
                    "-XX:MaxGCPauseMillis=100",
                    "-XX:+AlwaysPreTouch"
                )
                .addProfiler(GCProfiler.class);
        
        // Проверяем, что директория создана и доступна для записи
        if (!java.nio.file.Files.isWritable(resultsPath)) {
            System.err.println("WARNING: Results directory is not writable: " + resultsPath.toAbsolutePath());
            // Используем временную директорию как запасной вариант
            String tempDir = System.getProperty("java.io.tmpdir");
            System.out.println("Using temp directory instead: " + tempDir);
            
            optBuilder.result(tempDir + "/jmh-results.json");
        } else {
            optBuilder.result("./results/multithread-results.json");
        }
        Options opt = optBuilder.build();
        new Runner(opt).run();
    }
}