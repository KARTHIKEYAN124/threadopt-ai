package com.threadopt;
import org.junit.jupiter.api.Test;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class PoolTest {
 @Test void noLostOrDuplicatedTasks()throws Exception{var seen=ConcurrentHashMap.<Integer>newKeySet();try(var pool=new CustomThreadPool(4,CustomThreadPool.Policy.FIFO,1000)){for(int i=0;i<1000;i++){int id=i;pool.submit(0,1,()->assertTrue(seen.add(id)));}pool.resize(2);pool.resize(8);pool.shutdown();assertTrue(pool.await(10000));assertEquals(1000,pool.completed.get());assertEquals(0,pool.failed.get());}assertEquals(1000,seen.size());}
 @Test void policiesOrderQueuedTasks()throws Exception{for(var policy:CustomThreadPool.Policy.values()){var gate=new CountDownLatch(1);var started=new CountDownLatch(1);var result=Collections.synchronizedList(new ArrayList<Integer>());try(var p=new CustomThreadPool(1,policy,10)){p.submit(0,1,()->{started.countDown();try{gate.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(started.await(2,TimeUnit.SECONDS));p.submit(1,30,()->result.add(1));p.submit(3,20,()->result.add(2));p.submit(2,10,()->result.add(3));gate.countDown();p.shutdown();assertTrue(p.await(5000));assertEquals(switch(policy){case FIFO->List.of(1,2,3);case PRIORITY->List.of(2,3,1);case SJF->List.of(3,2,1);},result);}}}
 @Test void exceptionsDoNotKillWorkers()throws Exception{try(var p=new CustomThreadPool(1,CustomThreadPool.Policy.FIFO,10)){p.submit(0,1,()->{throw new RuntimeException();});p.submit(0,1,()->{});p.shutdown();assertTrue(p.await(2000));assertEquals(1,p.failed.get());assertEquals(1,p.completed.get());assertThrows(RejectedExecutionException.class,()->p.submit(0,1,()->{}));}}
 @Test void bounds(){assertThrows(IllegalArgumentException.class,()->new CustomThreadPool(0,CustomThreadPool.Policy.FIFO,10));}
 @Test void cancellationStopsCooperativeWork()throws Exception{
  var started=new CountDownLatch(1);
  var p=new CustomThreadPool(1,CustomThreadPool.Policy.FIFO,10);
  p.submit(0,1000,()->{started.countDown();try{Thread.sleep(10000);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
  assertTrue(started.await(2,TimeUnit.SECONDS));p.submit(0,1,()->fail("Queued work must be discarded"));p.cancel();assertTrue(p.await(2000));assertEquals(0,((Number)p.snapshot().get("queued")).intValue());
 }
 @Test void agingLetsOldestTaskRun()throws Exception{
  var gate=new CountDownLatch(1);var started=new CountDownLatch(1);var order=Collections.synchronizedList(new ArrayList<Integer>());
  try(var p=new CustomThreadPool(1,CustomThreadPool.Policy.PRIORITY,10)){
   p.submit(0,1,()->{started.countDown();try{gate.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
   assertTrue(started.await(2,TimeUnit.SECONDS));p.submit(0,1,()->order.add(1));Thread.sleep(2050);p.submit(10,1,()->order.add(2));gate.countDown();p.shutdown();assertTrue(p.await(5000));assertEquals(List.of(1,2),order);
  }
 }
}
