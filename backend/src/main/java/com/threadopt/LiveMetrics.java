package com.threadopt;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
@Configuration @EnableWebSocket
public class LiveMetrics implements WebSocketConfigurer {
 private final Experiments experiments;private final ObjectMapper json;
 private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
 private final ConcurrentHashMap<String,WebSocketSession> sessions=new ConcurrentHashMap<>();
 public LiveMetrics(Experiments e,ObjectMapper j){experiments=e;json=j;timer.scheduleAtFixedRate(()->sessions.forEach((id,s)->{try{if(s.isOpen())s.sendMessage(new TextMessage(json.writeValueAsString(experiments.live())));else sessions.remove(id);}catch(Exception ex){sessions.remove(id);try{s.close();}catch(Exception ignored){}}}),0,500,TimeUnit.MILLISECONDS);}
 public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){registry.addHandler(new TextWebSocketHandler(){public void afterConnectionEstablished(WebSocketSession s){sessions.put(s.getId(),s);}public void afterConnectionClosed(WebSocketSession s,CloseStatus status){sessions.remove(s.getId());}},"/ws/metrics");}
 @PreDestroy public void stop(){timer.shutdownNow();}
}
