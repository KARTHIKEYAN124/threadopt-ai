package com.threadopt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"})
class ApiTest {
 @Autowired TestRestTemplate api;
 @Test void lifecycleAndValidation()throws Exception {
  var invalid=Map.of("tasks",1001,"workers",1,"durationMs",5,"policy","FIFO","workload","IO","seed",42);
  assertEquals(HttpStatus.BAD_REQUEST,api.postForEntity("/api/experiments",invalid,Map.class).getStatusCode());
  var config=Map.of("tasks",50,"workers",1,"durationMs",30,"policy","PRIORITY","workload","IO","seed",42);
  var created=api.postForEntity("/api/experiments",config,Map.class);assertEquals(HttpStatus.ACCEPTED,created.getStatusCode());
  String id=(String)created.getBody().get("id");
  assertEquals(HttpStatus.CONFLICT,api.postForEntity("/api/experiments",config,Map.class).getStatusCode());
  var cancelled=api.postForEntity("/api/experiments/"+id+"/cancel",Map.of(),Map.class);
  assertEquals("CANCELLED",cancelled.getBody().get("status"));
  assertEquals(HttpStatus.NOT_FOUND,api.getForEntity("/api/experiments/missing",Map.class).getStatusCode());
  assertTrue(api.getForObject("/api/experiments",List.class).size()>0);
 }
}
