package com.ycyw.poc.web;

import com.ycyw.poc.chat.ChatService;
import com.ycyw.poc.chat.StreamService;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class ChatController {
    private final ChatService chat;private final StreamService streams;
    private final Map<String,Window> windows=new ConcurrentHashMap<>();
    private record Window(long minute,int count){}
    public record SendMessage(UUID clientMessageId,String text){}
    public record CreateConversation(UUID reservationId){}
    public ChatController(ChatService chat,StreamService streams){this.chat=chat;this.streams=streams;}
    @GetMapping("/health") public Map<String,String> health(){return Map.of("status","UP");}
    @GetMapping("/csrf") public ResponseEntity<Void> csrf(CsrfToken csrf){csrf.getToken();return ResponseEntity.noContent().build();}
    @GetMapping("/session") public ChatService.Actor session(Principal p){return chat.actor(p.getName());}
    @GetMapping("/conversations") public Object list(Principal p){return chat.list(p.getName());}
    @PostMapping("/conversations") public Object create(Principal p,@RequestHeader("Idempotency-Key") UUID key,@RequestBody CreateConversation body){
        if(body.reservationId()!=null) throw new ApiProblem(422,"CONTEXT_OUT_OF_SCOPE","Les réservations sont hors périmètre de cette PoC");
        return chat.create(p.getName(),key);
    }
    @GetMapping("/conversations/{id}") public Object get(@PathVariable UUID id,Principal p){return chat.get(id,p.getName());}
    @PostMapping("/support/conversations/{id}/claim") public Object claim(@PathVariable UUID id,Principal p){return chat.claim(id,p.getName());}
    @PostMapping("/support/conversations/{id}/release") public Object release(@PathVariable UUID id,Principal p){return chat.release(id,p.getName());}
    @PostMapping("/conversations/{id}/closure") public Object close(@PathVariable UUID id,Principal p,@RequestHeader("If-Match") String version){return chat.close(id,p.getName(),Long.parseLong(version.replace("\"","")));}
    @GetMapping("/conversations/{id}/messages") public Object messages(@PathVariable UUID id,Principal p,@RequestParam(required=false) Long afterSequence,@RequestParam(required=false) Long beforeSequence,@RequestParam(defaultValue="50") int limit){return chat.history(id,p.getName(),afterSequence,beforeSequence,limit);}
    @PostMapping("/conversations/{id}/messages") public ResponseEntity<?> send(@PathVariable UUID id,Principal p,@RequestBody SendMessage body){
        limit(p.getName());var result=chat.send(id,p.getName(),body.clientMessageId(),body.text());
        return ResponseEntity.status(result.created()?201:200).body(result.message());
    }
    @GetMapping(value="/conversations/{id}/events",produces="text/event-stream")
    public SseEmitter events(@PathVariable UUID id,Principal p,HttpServletRequest request,@RequestParam(defaultValue="0") long after,@RequestHeader(value="Last-Event-ID",required=false) String last){
        return streams.open(id,p.getName(),request.getSession(false).getId(),last==null||last.isBlank()?after:Long.parseLong(last));
    }
    private void limit(String username){
        long minute=System.currentTimeMillis()/60_000;
        Window w=windows.compute(username,(k,old)->old==null||old.minute()!=minute?new Window(minute,1):new Window(minute,old.count()+1));
        if(w.count()>120) throw new ApiProblem(429,"RATE_LIMIT","Limite locale de 120 tentatives par minute atteinte");
    }
}
