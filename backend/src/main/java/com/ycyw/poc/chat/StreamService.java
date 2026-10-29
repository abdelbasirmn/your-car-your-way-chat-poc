package com.ycyw.poc.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.ycyw.poc.web.ApiProblem;

@Service
public class StreamService {
    private final ChatService chat; private final JdbcIndexedSessionRepository sessions;
    private final Map<UUID,Connection> connections=new ConcurrentHashMap<>();
    private static class Connection {
        final UUID conversation;final String username;final String session;final SseEmitter emitter;
        long cursor;long heartbeat=0;
        Connection(UUID c,String u,String s,long cursor,SseEmitter e){conversation=c;username=u;session=s;this.cursor=cursor;emitter=e;}
    }
    public StreamService(ChatService chat,JdbcIndexedSessionRepository sessions){this.chat=chat;this.sessions=sessions;}
    public SseEmitter open(UUID conversation,String username,String session,long cursor){
        chat.events(conversation,username,cursor);
        long same=connections.values().stream().filter(c->c.session.equals(session)).count();
        if(same>=4 || connections.size()>=40) throw new ApiProblem(429,"STREAM_LIMIT","Fermez les autres fenêtres de tchat avant de réessayer");
        UUID id=UUID.randomUUID();SseEmitter emitter=new SseEmitter(120_000L);
        Connection c=new Connection(conversation,username,session,cursor,emitter);
        emitter.onCompletion(()->connections.remove(id));
        emitter.onTimeout(()->{connections.remove(id);emitter.complete();});
        emitter.onError(e->connections.remove(id));
        connections.put(id,c);return emitter;
    }
    @Scheduled(fixedDelayString="${poc.stream-poll-ms:1000}")
    public void tick(){
        connections.forEach((id,c)->{
            try {
                if(sessions.findById(c.session)==null){connections.remove(id);c.emitter.complete();return;}
                // Re-evaluate session, account and membership before reading any new references.
                for(var e:chat.events(c.conversation,c.username,c.cursor)){
                    chat.get(c.conversation,c.username);
                    if(sessions.findById(c.session)==null){connections.remove(id);c.emitter.complete();return;}
                    var data=e.messageId()==null?Map.of("sequence",e.sequence()):Map.of("sequence",e.sequence(),"messageId",e.messageId().toString());
                    c.emitter.send(SseEmitter.event().id(Long.toString(e.sequence())).name(e.kind()).data(data));
                    c.cursor=e.sequence();
                }
                if(System.currentTimeMillis()-c.heartbeat>=15_000){c.emitter.send(SseEmitter.event().comment("keep-alive"));c.heartbeat=System.currentTimeMillis();}
            }catch(Exception e){connections.remove(id);c.emitter.complete();}
        });
    }
}
