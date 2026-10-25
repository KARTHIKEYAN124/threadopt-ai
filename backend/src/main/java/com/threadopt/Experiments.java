package com.threadopt;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@RestController @RequestMapping("/api")
public class Experiments {
 public record Config(@Min(1) @Max(1000) int tasks,@Min(1) @Max(32) int workers,@Min(1) @Max(100) int durationMs,@NotNull CustomThreadPool.Policy policy,@Pattern(regexp="CPU|IO|MIXED") @NotNull String workload,long seed){}
 private final JdbcTemplate db; private final ObjectMapper json;
 private final ExecutorService coordinator=Executors.newSingleThreadExecutor();
 private volatile Run active;
 private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(1)).build();
 private static volatile long sink;
 private static long processCpu(){var bean=java.lang.management.ManagementFactory.getOperatingSystemMXBean();return bean instanceof com.sun.management.OperatingSystemMXBean os?os.getProcessCpuTime():-1;}
 private static class Run {String id=UUID.randomUUID().toString();Config config;volatile String status="RUNNING";volatile Map<String,Object> metrics=Map.of();CustomThreadPool pool;long cpuStarted=processCpu();long createdAt=System.currentTimeMillis();long started=System.nanoTime();Run(Config c){config=c;pool=new CustomThreadPool(c.workers,c.policy,1000);}}
 public Experiments(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;
  db.query("SELECT id,payload FROM experiments",rs->{try{Map data=json.readValue(rs.getString("payload"),Map.class);if("RUNNING".equals(data.get("status"))){data.put("status","INTERRUPTED");db.update("UPDATE experiments SET payload=? WHERE id=?",json.writeValueAsString(data),rs.getString("id"));}}catch(Exception ex){throw new IllegalStateException(ex);}});
 }
 @GetMapping("/health") public Object health(){return Map.of("status","ok","cores",Runtime.getRuntime().availableProcessors());}
 @GetMapping("/schedulers") public Object schedulers(){return CustomThreadPool.Policy.values();}
 @PostMapping("/experiments") public synchronized ResponseEntity<?> start(@Valid @RequestBody Config c){
  if(active!=null && active.status.equals("RUNNING"))throw new ResponseStatusException(HttpStatus.CONFLICT,"An experiment is already running");
  Run r=new Run(c);active=r;save(r);coordinator.submit(()->execute(r));return ResponseEntity.accepted().body(view(r));
 }
 private void execute(Run r){
  try{Random random=new Random(r.config.seed);for(int i=0;i<r.config.tasks && r.status.equals("RUNNING");i++){
   int duration=1+random.nextInt(r.config.durationMs*2),priority=random.nextInt(5);boolean io=r.config.workload.equals("IO")||(r.config.workload.equals("MIXED")&&random.nextBoolean());
   r.pool.submit(priority,duration,()->work(duration,io));
  }
  r.pool.shutdown();if(!r.pool.await(220000)){r.pool.cancel();r.status="TIMED_OUT";}else if(r.status.equals("RUNNING"))r.status=r.pool.failed.get()>0?"FAILED":"COMPLETED";
  }catch(Exception e){r.pool.cancel();if(r.status.equals("RUNNING"))r.status="FAILED";}
  finally{r.metrics=metrics(r);save(r);}
 }
 static void work(int ms,boolean io){if(io){try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new CancellationException();}}else{long end=System.nanoTime()+ms*1_000_000L;long n=1;while(System.nanoTime()<end){if(Thread.currentThread().isInterrupted())throw new CancellationException();for(int i=0;i<1000;i++)n=n*1664525+1013904223;}sink=n;}}
 private Map<String,Object> metrics(Run r){
  List<Double> latency,wait; synchronized(r.pool.latencies){latency=new ArrayList<>(r.pool.latencies);}synchronized(r.pool.waits){wait=new ArrayList<>(r.pool.waits);}Collections.sort(latency);
  double seconds=(System.nanoTime()-r.started)/1e9;
  Map<String,Object> result=new LinkedHashMap<>(Map.of("elapsedMs",seconds*1000,"throughput",r.pool.completed.get()/Math.max(seconds,.001),"averageWaitMs",wait.stream().mapToDouble(x->x).average().orElse(0),"p95LatencyMs",percentile(latency,.95),"p99LatencyMs",percentile(latency,.99),"maxWaitMs",wait.stream().mapToDouble(x->x).max().orElse(0),"completed",r.pool.completed.get(),"failed",r.pool.failed.get()));
  long cpuNow=processCpu();
  result.put("processCpuUtilization",r.cpuStarted<0||cpuNow<0?-1:Math.min(1.,Math.max(0.,(cpuNow-r.cpuStarted)/(seconds*1e9*Runtime.getRuntime().availableProcessors()))));
  result.put("workerUtilization",Math.min(1.,r.pool.executionNanos.get()/(seconds*1e9*r.config.workers)));
  result.put("queueSelectionMs",r.pool.selectionNanos.get()/1e6);
  return result;
 }
 private double percentile(List<Double> values,double p){return values.isEmpty()?0:values.get(Math.min(values.size()-1,(int)Math.ceil(values.size()*p)-1));}
 private Map<String,Object> view(Run r){return Map.of("id",r.id,"createdAt",r.createdAt,"config",r.config,"status",r.status,"metrics",r.metrics.isEmpty()?metrics(r):r.metrics,"pool",r.pool.snapshot());}
 private synchronized void save(Run r){try{String payload=json.writeValueAsString(view(r));int n=db.update("UPDATE experiments SET payload=? WHERE id=?",payload,r.id);if(n==0)db.update("INSERT INTO experiments(id,payload) VALUES (?,?)",r.id,payload);}catch(Exception e){throw new IllegalStateException("Cannot persist experiment",e);}}
 @GetMapping("/experiments") public Object list(){return db.query("SELECT payload FROM experiments",(rs,n)->{try{return json.readValue(rs.getString(1),Map.class);}catch(Exception e){throw new IllegalStateException(e);}});}
 @GetMapping("/experiments/{id}") public Object get(@PathVariable String id){Run r=active;if(r!=null&&r.id.equals(id))return view(r);return db.query("SELECT payload FROM experiments WHERE id=?",(rs,n)->{try{return json.readValue(rs.getString(1),Map.class);}catch(Exception e){throw new IllegalStateException(e);}},id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Experiment not found"));}
 @GetMapping("/experiments/{id}/metrics") public Object getMetrics(@PathVariable String id){return ((Map<?,?>)get(id)).get("metrics");}
 @PostMapping("/experiments/{id}/cancel") public synchronized Object cancel(@PathVariable String id){if(active==null||!active.id.equals(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);if(active.status.equals("RUNNING")){active.status="CANCELLED";active.pool.cancel();save(active);}return view(active);}
 @GetMapping("/metrics/live") public Object live(){return active==null?Map.of("status","IDLE"):view(active);}
 @PostMapping("/optimizer/recommend") public Object recommend(@RequestBody Map<String,Object> features){
  try{var request=HttpRequest.newBuilder(URI.create(System.getenv().getOrDefault("AI_SERVICE_URL","http://localhost:8000")+"/predict")).timeout(Duration.ofSeconds(3)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(features))).build();var response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalStateException();return json.readValue(response.body(),Map.class);}
  catch(Exception e){return Map.of("recommended_workers",Math.min(4,Runtime.getRuntime().availableProcessors()),"recommended_policy","FIFO","source","rule-based fallback","reason","Optimizer unavailable");}
 }
 @PreDestroy public void stop(){if(active!=null)active.pool.cancel();coordinator.shutdownNow();}
}
