package org.fedorov.uniq.lists;

import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.fedorov.uniq.lists.impl.AtomicBooleanLockedUniqueList;
import org.fedorov.uniq.lists.impl.ReentrantLockedUniqueList;
import org.fedorov.uniq.lists.impl.SuperValidVolatileLockedUniqueList;
import org.fedorov.uniq.lists.impl.SynchronizedMethodUniqueList;
import org.fedorov.uniq.lists.impl.SynchronizedSectionUniqueList;
import org.fedorov.uniq.lists.impl.ValidReentrantLockedUniqueList;
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
@BenchmarkMode({Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 5, time = 60)
@Fork(value = 1)
public class MultiThreadUniqueListBenchmark {

   public enum ListImplementation {
        SYNCHRONIZED_METHOD(SynchronizedMethodUniqueList.class.getName()),
        SYNCHRONIZED_SECTION(SynchronizedSectionUniqueList.class.getName()),
        ATOMIC_BOOLEAN(AtomicBooleanLockedUniqueList.class.getName()),
        VALID_VOLATILE(ValidVolatileLockedUniqueList.class.getName()),
        SUPER_VALID_VOLATILE(SuperValidVolatileLockedUniqueList.class.getName()),
        REENTRANT_LOCK(ReentrantLockedUniqueList.class.getName()),
        VALID_REENTRANT_LOCK(ValidReentrantLockedUniqueList.class.getName());
        
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
    private int LIST_SIZE;

    private IUniqueList<Integer> list;
    private final ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

    
    @Setup(Level.Iteration)
    public void setupIteration() throws Exception {
        Class<?> clazz = Class.forName(implementationName.getClassName());
        Constructor<?> constructor = clazz.getDeclaredConstructor();
        
        @SuppressWarnings("unchecked")
        IUniqueList<Integer> instance = (IUniqueList<Integer>) constructor.newInstance();
        this.list = instance;
        errors.clear();
    }
    
    @TearDown(Level.Iteration)
    public void tearDownIteration(Blackhole blackhole) {
        if (!errors.isEmpty()) {
            blackhole.consume("Errors: " + errors.size());
        }
        
        // Проверяем результат
        int actualSize = list.size();
        int expectedSize = LIST_SIZE;
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

    private void runBenchmark(Blackhole blackhole) {
        boolean added = list.add(ThreadLocalRandom.current().nextInt(LIST_SIZE));
        blackhole.consume(added);
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

    @Benchmark
    @Threads(32)
    public void testWith32Threads(Blackhole blackhole) {
        runBenchmark(blackhole);
    }

    @Benchmark
    @Threads(64)
    public void testWith64Threads(Blackhole blackhole) {
        runBenchmark(blackhole);
    }

    @Benchmark
    @Threads(128)
    public void testWith128Threads(Blackhole blackhole) {
        runBenchmark(blackhole);
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