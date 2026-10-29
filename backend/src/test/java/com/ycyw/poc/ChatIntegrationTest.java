package com.ycyw.poc;

import com.ycyw.poc.chat.ChatService;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired ChatService chat;
    @Autowired JdbcTemplate db;
    final ObjectMapper json=new ObjectMapper();
    class Browser {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<String> call(String path,String method,String body,boolean csrf,Map<String,String> headers)throws Exception{
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).timeout(Duration.ofSeconds(15));
            headers.forEach(b::header);
            if(csrf)cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("XSRF-TOKEN")).findFirst().ifPresent(c->b.header("X-XSRF-TOKEN",c.getValue()));
            if(body!=null && !path.equals("/session"))b.header("Content-Type","application/json");
            if(path.equals("/session")&&method.equals("POST"))b.header("Content-Type","application/x-www-form-urlencoded");
            return client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> get(String p)throws Exception{return call(p,"GET",null,false,Map.of());}
        HttpResponse<String> post(String p,String body)throws Exception{return call(p,"POST",body,true,Map.of());}
        void login(String name,String pass)throws Exception{
            assertEquals(204,get("/csrf").statusCode());
            assertEquals(200,call("/session","POST","username="+URLEncoder.encode(name,StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(pass,StandardCharsets.UTF_8),true,Map.of()).statusCode());
            assertEquals(204,get("/csrf").statusCode());
        }
    }
    Browser alice()throws Exception{var b=new Browser();b.login("alice@example.test","Demo-Alice-2026!");return b;}
    Browser support()throws Exception{var b=new Browser();b.login("support@example.test","Demo-Support-2026!");return b;}
    UUID create(Browser b)throws Exception{
        var r=b.call("/conversations","POST","{}",true,Map.of("Idempotency-Key",UUID.randomUUID().toString()));
        assertTrue(r.statusCode()==200||r.statusCode()==201,r.body());return UUID.fromString(json.readTree(r.body()).get("id").asText());
    }
    String payload(UUID key,String text)throws Exception{return json.writeValueAsString(Map.of("clientMessageId",key,"text",text));}

    @Test void authenticationAndCsrfAreReal()throws Exception{
        Browser b=new Browser();assertEquals(401,b.get("/conversations").statusCode());
        b.get("/csrf");assertEquals(401,b.call("/session","POST","username=alice%40example.test&password=wrong",true,Map.of()).statusCode());
        b.login("alice@example.test","Demo-Alice-2026!");
        assertEquals(403,b.call("/conversations","POST","{}",false,Map.of("Idempotency-Key",UUID.randomUUID().toString())).statusCode());
        assertEquals(204,b.post("/logout",null).statusCode());assertEquals(401,b.get("/conversations").statusCode());
    }
    @Test void privateConversationRequiresOwnershipOrAssignment()throws Exception{
        Browser a=alice();UUID id=create(a);Browser b=new Browser();b.login("bob@example.test","Demo-Bob-2026!");
        assertEquals(404,b.get("/conversations/"+id+"/messages").statusCode());
        assertEquals(404,b.post("/conversations/"+id+"/messages",payload(UUID.randomUUID(),"Interdit")).statusCode());
        Browser s=support();assertEquals(404,s.get("/conversations/"+id+"/messages").statusCode());
        assertEquals(200,s.post("/support/conversations/"+id+"/claim",null).statusCode());
        assertEquals(200,s.get("/conversations/"+id+"/messages").statusCode());
        Browser other=new Browser();other.login("other-support@example.test","Demo-Other-2026!");
        assertEquals(404,other.get("/conversations/"+id+"/messages").statusCode());
        s.post("/support/conversations/"+id+"/release",null);
        assertEquals(404,s.get("/conversations/"+id+"/messages").statusCode());
    }
    @Test void retryIsIdempotentAndClosedConversationCannotAppend()throws Exception{
        Browser a=alice();UUID id=create(a),key=UUID.randomUUID();String text="<script>window.pwned=true</script>";
        var first=a.post("/conversations/"+id+"/messages",payload(key,text));assertEquals(201,first.statusCode(),first.body());
        var again=a.post("/conversations/"+id+"/messages",payload(key,text));assertEquals(200,again.statusCode());
        assertEquals(json.readTree(first.body()).get("id"),json.readTree(again.body()).get("id"));
        assertEquals(409,a.post("/conversations/"+id+"/messages",payload(key,"Autre texte")).statusCode());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM message WHERE conversation_id=?",Integer.class,id));
        long version=chat.get(id,"alice@example.test").version();
        assertEquals(200,a.call("/conversations/"+id+"/closure","POST",null,true,Map.of("If-Match",Long.toString(version))).statusCode());
        assertEquals(200,a.post("/conversations/"+id+"/messages",payload(key,text)).statusCode());
        assertEquals(409,a.post("/conversations/"+id+"/messages",payload(UUID.randomUUID(),"Nouveau")).statusCode());
    }
    @Test void validationAndPaginationDoNotLoseMessages()throws Exception{
        Browser a=alice();UUID id=create(a);
        assertEquals(422,a.post("/conversations/"+id+"/messages",payload(UUID.randomUUID(),"   ")).statusCode());
        assertEquals(422,a.post("/conversations/"+id+"/messages",payload(UUID.randomUUID(),"a".repeat(2001))).statusCode());
        for(int i=0;i<105;i++)chat.send(id,"alice@example.test",UUID.randomUUID(),"Historique "+i);
        var latest=chat.history(id,"alice@example.test",null,null,50);assertEquals(50,latest.items().size());assertTrue(latest.hasMore());
        var older=chat.history(id,"alice@example.test",null,latest.nextMessageCursor(),50);assertEquals(50,older.items().size());
        var last=chat.history(id,"alice@example.test",null,older.nextMessageCursor(),50);assertEquals(5,last.items().size());assertFalse(last.hasMore());
        var forward=chat.history(id,"alice@example.test",0L,null,100);assertEquals(100,forward.items().size());assertTrue(forward.hasMore());
        var end=chat.history(id,"alice@example.test",forward.nextMessageCursor(),null,100);assertEquals(5,end.items().size());assertFalse(end.hasMore());
    }
    @Test void concurrentMessagesHaveUniqueStableSequences()throws Exception{
        Browser a=alice();UUID id=create(a);ExecutorService pool=Executors.newFixedThreadPool(4);
        try{List<Callable<Long>> calls=new ArrayList<>();for(int i=0;i<12;i++){final int n=i;calls.add(()->chat.send(id,"alice@example.test",UUID.randomUUID(),"Concurrent "+n).message().sequence());}
            Set<Long> sequences=new HashSet<>();for(var f:pool.invokeAll(calls))sequences.add(f.get(30,TimeUnit.SECONDS));assertEquals(12,sequences.size());
            assertEquals(12,db.queryForObject("SELECT count(*) FROM conversation_event WHERE conversation_id=?",Integer.class,id));
        }finally{pool.shutdownNow();}
    }
    @Test void expiredSessionCannotReadPrivateHistory()throws Exception{
        Browser a=alice();UUID id=create(a);
        db.update("UPDATE spring_session SET last_access_time=0, expiry_time=0 WHERE principal_name=?","alice@example.test");
        assertEquals(401,a.get("/conversations/"+id+"/messages").statusCode());
    }
    @Test void sseReplaysPersistedEventsAfterCursor()throws Exception{
        Browser a=alice();UUID id=create(a);chat.send(id,"alice@example.test",UUID.randomUUID(),"Avant coupure");chat.send(id,"alice@example.test",UUID.randomUUID(),"Pendant coupure");
        HttpRequest request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversations/"+id+"/events?after=1")).build();
        var response=a.client.sendAsync(request,HttpResponse.BodyHandlers.ofInputStream()).get(15,TimeUnit.SECONDS);
        assertEquals(200,response.statusCode());
        try(var input=response.body();var reader=new java.io.BufferedReader(new java.io.InputStreamReader(input))){
            ExecutorService pool=Executors.newSingleThreadExecutor();try{
                String event=pool.submit(()->{StringBuilder lines=new StringBuilder();String line;while((line=reader.readLine())!=null){lines.append(line).append('\n');if(line.contains("data:"))break;}return lines.toString();}).get(10,TimeUnit.SECONDS);
                assertTrue(event.contains("id:2"),event);assertTrue(event.contains("message.created"),event);
            }finally{pool.shutdownNow();}
        }
    }
}
