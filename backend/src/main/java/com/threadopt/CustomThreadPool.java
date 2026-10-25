package com.threadopt;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** A bounded monitor-protected queue; platform threads execute the work. */
public final class CustomThreadPool implements AutoCloseable {
 public enum Policy { FIFO, PRIORITY, SJF }
 public record Task(long sequence, int priority, long estimateMs, Runnable action, long submitted) {}
 private final List<Task> queue=new ArrayList<>();
 private final List<Worker> workers=new ArrayList<>();
 private final int capacity; private Policy policy; private boolean accepting=true, stopped=false;
 private final AtomicLong sequence=new AtomicLong();
 public final AtomicLong executionNanos=new AtomicLong(), selectionNanos=new AtomicLong();
 public final AtomicInteger completed=new AtomicInteger(), failed=new AtomicInteger();
 public final List<Double> waits=Collections.synchronizedList(new ArrayList<>()), latencies=Collections.synchronizedList(new ArrayList<>());
 private class Worker extends Thread {
  volatile boolean retire=false, busy=false;
  Worker(int id){super("threadopt-worker-"+id);}
  public void run(){
   while(true){ Task task;
    synchronized(CustomThreadPool.this){
     while(queue.isEmpty() && accepting && !retire && !stopped) try{CustomThreadPool.this.wait();}catch(InterruptedException e){if(stopped)return;}
     if(stopped || retire || (queue.isEmpty() && !accepting))return;
     long selecting=System.nanoTime();task=take();selectionNanos.addAndGet(System.nanoTime()-selecting); busy=true;
    }
    long start=System.nanoTime();
    try{ task.action.run(); completed.incrementAndGet(); }catch(Throwable e){failed.incrementAndGet();}
    finally{long end=System.nanoTime();executionNanos.addAndGet(end-start); waits.add((start-task.submitted)/1e6);latencies.add((end-task.submitted)/1e6);busy=false;}
   }
  }
 }
 public CustomThreadPool(int count, Policy policy,int capacity){this.policy=Objects.requireNonNull(policy);this.capacity=capacity;resize(count);}
 public synchronized void submit(int priority,long estimateMs,Runnable action){
  Objects.requireNonNull(action);
  if(!accepting || stopped)throw new RejectedExecutionException("Pool closed");
  if(queue.size()>=capacity)throw new RejectedExecutionException("Queue capacity reached");
  queue.add(new Task(sequence.getAndIncrement(),priority,estimateMs,action,System.nanoTime()));notifyAll();
 }
 private Task take(){
  long now=System.nanoTime();
  // Tasks waiting over two seconds become FIFO-eligible to bound starvation.
  Comparator<Task> comparator=Comparator.comparingLong(Task::sequence);
  if(queue.stream().noneMatch(t->now-t.submitted>2_000_000_000L)) comparator=switch(policy){
   case FIFO -> comparator;
   case PRIORITY -> Comparator.comparingInt(Task::priority).reversed().thenComparingLong(Task::sequence);
   case SJF -> Comparator.comparingLong(Task::estimateMs).thenComparingLong(Task::sequence);
  };
  Task task=Collections.min(queue,comparator);queue.remove(task);return task;
 }
 public synchronized void resize(int count){
  if(count<1 || count>32)throw new IllegalArgumentException("Workers must be 1..32");
  if(!accepting)throw new IllegalStateException("Pool closed");
  int active=(int)workers.stream().filter(w->!w.retire && w.isAlive()).count();
  long live=workers.stream().filter(Thread::isAlive).count();
  if(count>active && live+count-active>32)throw new RejectedExecutionException("Wait for retiring workers before growing the pool");
  for(Worker w:workers)if(active>count && !w.retire){w.retire=true;active--;}
  while(active<count){Worker w=new Worker(workers.size()+1);workers.add(w);active++;w.start();}notifyAll();
 }
 public synchronized Map<String,Object> snapshot(){return Map.of("queued",queue.size(),"workers",workers.stream().filter(w->w.isAlive()).map(w->Map.of("name",w.getName(),"busy",w.busy,"retiring",w.retire)).toList(),"completed",completed.get(),"failed",failed.get());}
 public synchronized void shutdown(){accepting=false;notifyAll();}
 public synchronized void cancel(){accepting=false;stopped=true;queue.clear();workers.forEach(Thread::interrupt);notifyAll();}
 public boolean await(long timeoutMs)throws InterruptedException {long deadline=System.nanoTime()+timeoutMs*1_000_000;for(Worker w:List.copyOf(workers)){long remaining=(deadline-System.nanoTime())/1_000_000;if(remaining<=0)return false;w.join(Math.max(1,remaining));}return workers.stream().noneMatch(Thread::isAlive);}
 public void close(){shutdown();try{if(!await(30000)){cancel();await(2000);}}catch(InterruptedException e){cancel();Thread.currentThread().interrupt();}}
}
