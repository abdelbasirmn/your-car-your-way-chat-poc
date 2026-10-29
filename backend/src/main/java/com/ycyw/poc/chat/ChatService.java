package com.ycyw.poc.chat;

import com.ycyw.poc.web.ApiProblem;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
    private final JdbcTemplate db;
    public ChatService(JdbcTemplate db){this.db=db;}
    public record Actor(UUID id,String email,String displayName,String role) {}
    public record Conversation(UUID id,UUID ownerId,UUID assignedAgentId,String ownerName,String status,long version,long eventCursor) {}
    public record Message(UUID id,long sequence,UUID clientMessageId,String senderRole,String text,Instant createdAt,String status) {}
    public record Page(List<Message> items,long eventCursor,Long nextMessageCursor,boolean hasMore) {}
    public record Recorded(Message message,boolean created) {}
    public record Event(long sequence,String kind,UUID messageId) {}
    private static final String VIEW="SELECT c.*,a.display_name owner_name FROM conversation c JOIN account a ON c.owner_id=a.id ";
    private final RowMapper<Conversation> conversationMapper=(r,n)->new Conversation(r.getObject("id",UUID.class),r.getObject("owner_id",UUID.class),r.getObject("assigned_agent_id",UUID.class),r.getString("owner_name"),r.getString("status"),r.getLong("version"),r.getLong("last_sequence"));
    private final RowMapper<Message> messageMapper=(r,n)->new Message(r.getObject("id",UUID.class),r.getLong("sequence"),r.getObject("client_message_id",UUID.class),r.getString("role"),r.getString("body"),r.getTimestamp("created_at").toInstant(),"RECORDED");
    private static final String MESSAGES="SELECT m.*,a.role FROM message m JOIN account a ON a.id=m.sender_id ";

    public Actor actor(String email){
        var rows=db.query("SELECT * FROM account WHERE email=? AND active=true",(r,n)->new Actor(r.getObject("id",UUID.class),r.getString("email"),r.getString("display_name"),r.getString("role")),email);
        if(rows.isEmpty()) throw new ApiProblem(401,"UNAUTHENTICATED","Connexion requise");
        return rows.getFirst();
    }
    public List<Conversation> list(String email){
        Actor a=actor(email);
        // WAITING metadata is a support work queue; it grants no access to messages.
        return db.query(VIEW+"WHERE c.owner_id=? OR c.assigned_agent_id=? OR (?='SUPPORT' AND c.status='WAITING' AND c.assigned_agent_id IS NULL) ORDER BY c.created_at DESC,c.id LIMIT 100",conversationMapper,a.id(),a.id(),a.role());
    }
    public Conversation get(UUID id,String email){return readable(id,actor(email));}
    private Conversation find(UUID id,boolean lock){
        var rows=db.query(VIEW+"WHERE c.id=?"+(lock?" FOR UPDATE OF c":""),conversationMapper,id);
        if(rows.isEmpty()) throw new ApiProblem(404,"NOT_FOUND","Conversation inaccessible");
        return rows.getFirst();
    }
    private Conversation readable(UUID id,Actor a){
        var c=find(id,false);
        if(!c.ownerId().equals(a.id()) && !(a.role().equals("SUPPORT") && a.id().equals(c.assignedAgentId())))
            throw new ApiProblem(404,"NOT_FOUND","Conversation inaccessible");
        return c;
    }
    @Transactional
    public Conversation create(String email,UUID key){
        Actor a=actor(email);
        if(!a.role().equals("CLIENT")) throw new ApiProblem(403,"FORBIDDEN","Seul un client ouvre une demande");
        db.update("INSERT INTO conversation(id,owner_id,creation_key) VALUES(?,?,?) ON CONFLICT(owner_id,creation_key) DO NOTHING",UUID.randomUUID(),a.id(),key);
        return db.query(VIEW+"WHERE owner_id=? AND creation_key=?",conversationMapper,a.id(),key).getFirst();
    }
    @Transactional
    public Conversation claim(UUID id,String email){
        Actor a=actor(email);
        if(!a.role().equals("SUPPORT")) throw new ApiProblem(403,"FORBIDDEN","Action réservée au support");
        Conversation c=find(id,true);
        if(a.id().equals(c.assignedAgentId())) return c;
        if(c.assignedAgentId()!=null || !c.status().equals("WAITING")) throw new ApiProblem(409,"ALREADY_ASSIGNED","Demande déjà prise en charge");
        db.update("UPDATE conversation SET assigned_agent_id=?,status='ACTIVE' WHERE id=?",a.id(),id);
        event(id,"assigned",null);
        return find(id,false);
    }
    @Transactional
    public Conversation release(UUID id,String email){
        Actor a=actor(email);Conversation c=find(id,true);readable(id,a);
        if(!a.role().equals("SUPPORT") || !a.id().equals(c.assignedAgentId()) || c.status().equals("CLOSED")) throw new ApiProblem(403,"FORBIDDEN","Affectation non modifiable");
        db.update("UPDATE conversation SET assigned_agent_id=NULL,status='WAITING' WHERE id=?",id);
        event(id,"released",null);return find(id,false);
    }
    @Transactional
    public Conversation close(UUID id,String email,long version){
        Actor a=actor(email);Conversation c=find(id,true);readable(id,a);
        if(c.version()!=version) throw new ApiProblem(412,"VERSION_CONFLICT","La conversation a changé ; actualisez son état");
        if(!c.status().equals("CLOSED")){db.update("UPDATE conversation SET status='CLOSED' WHERE id=?",id);event(id,"closed",null);}
        return find(id,false);
    }
    @Transactional
    public Recorded send(UUID id,String email,UUID clientId,String text){
        Actor a=actor(email);Conversation c=find(id,true);readable(id,a);
        if(clientId==null || text==null || text.isBlank() || text.codePointCount(0,text.length())>2000)
            throw new ApiProblem(422,"INVALID_MESSAGE","Écrivez un texte de 1 à 2 000 caractères, sans le tronquer");
        var previous=db.query(MESSAGES+"WHERE m.conversation_id=? AND m.sender_id=? AND m.client_message_id=?",messageMapper,id,a.id(),clientId);
        if(!previous.isEmpty()){
            var m=previous.getFirst();
            if(!m.text().equals(text)) throw new ApiProblem(409,"IDEMPOTENCY_CONFLICT","Identifiant déjà utilisé pour un autre texte");
            return new Recorded(m,false);
        }
        if(c.status().equals("CLOSED")) throw new ApiProblem(409,"CLOSED","Cette conversation est clôturée");
        long seq=next(id);UUID mid=UUID.randomUUID();
        db.update("INSERT INTO message(id,conversation_id,sender_id,client_message_id,sequence,body) VALUES(?,?,?,?,?,?)",mid,id,a.id(),clientId,seq,text);
        db.update("INSERT INTO conversation_event(conversation_id,sequence,kind,message_id) VALUES(?,?,'message.created',?)",id,seq,mid);
        return new Recorded(db.query(MESSAGES+"WHERE m.id=?",messageMapper,mid).getFirst(),true);
    }
    private long next(UUID id){
        Long seq=db.queryForObject("UPDATE conversation SET last_sequence=last_sequence+1,version=version+1 WHERE id=? AND last_sequence<9007199254740991 RETURNING last_sequence",Long.class,id);
        if(seq==null) throw new ApiProblem(409,"SEQUENCE_LIMIT","Limite de séquence atteinte");
        return seq;
    }
    private void event(UUID id,String kind,UUID messageId){
        db.update("INSERT INTO conversation_event(conversation_id,sequence,kind,message_id) VALUES(?,?,?,?)",id,next(id),kind,messageId);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page history(UUID id,String email,Long after,Long before,int limit){
        Conversation c=readable(id,actor(email));
        if(limit<1 || limit>100 || (after!=null && before!=null) || (after!=null && (after<0 || after>c.eventCursor())) || (before!=null && before<1))
            throw new ApiProblem(400,"INVALID_CURSOR","Curseur ou taille de page invalide");
        boolean forward=after!=null;
        List<Message> rows;
        if(forward) rows=db.query(MESSAGES+"WHERE m.conversation_id=? AND m.sequence>? AND m.sequence<=? ORDER BY m.sequence LIMIT ?",messageMapper,id,after,c.eventCursor(),limit+1);
        else if(before!=null) rows=db.query(MESSAGES+"WHERE m.conversation_id=? AND m.sequence<? ORDER BY m.sequence DESC LIMIT ?",messageMapper,id,before,limit+1);
        else rows=db.query(MESSAGES+"WHERE m.conversation_id=? ORDER BY m.sequence DESC LIMIT ?",messageMapper,id,limit+1);
        boolean more=rows.size()>limit;
        List<Message> items=new ArrayList<>(rows.subList(0,Math.min(limit,rows.size())));
        if(!forward) Collections.reverse(items);
        Long cursor=items.isEmpty()?null:(forward?items.getLast().sequence():items.getFirst().sequence());
        return new Page(items,c.eventCursor(),cursor,more);
    }
    public List<Event> events(UUID id,String email,long after){
        Conversation c=readable(id,actor(email));
        if(after<0 || after>c.eventCursor()) throw new ApiProblem(409,"INVALID_CURSOR","Resynchronisation nécessaire");
        return db.query("SELECT sequence,kind,message_id FROM conversation_event WHERE conversation_id=? AND sequence>? ORDER BY sequence LIMIT 100",(r,n)->new Event(r.getLong(1),r.getString(2),r.getObject(3,UUID.class)),id,after);
    }
}
