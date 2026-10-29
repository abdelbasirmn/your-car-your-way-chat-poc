package com.ycyw.poc.security;

import java.util.Arrays;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DemoData implements ApplicationRunner {
    private final JdbcTemplate db; private final PasswordEncoder encoder; private final Environment env;
    public DemoData(JdbcTemplate db,PasswordEncoder encoder,Environment env){this.db=db;this.encoder=encoder;this.env=env;}
    @Override public void run(ApplicationArguments args) {
        var profiles=Arrays.asList(env.getActiveProfiles());
        if(profiles.isEmpty() || profiles.stream().anyMatch(p -> !p.equals("local") && !p.equals("test")))
            throw new IllegalStateException("Cette PoC exige uniquement le profil explicite local ou test ; aucun usage de production.");
        seed("alice@example.test","Alice","CLIENT","Demo-Alice-2026!");
        seed("bob@example.test","Bob","CLIENT","Demo-Bob-2026!");
        seed("support@example.test","Support","SUPPORT","Demo-Support-2026!");
        seed("other-support@example.test","Autre support","SUPPORT","Demo-Other-2026!");
    }
    private void seed(String email,String name,String role,String password){
        Integer count=db.queryForObject("SELECT count(*) FROM account WHERE email=?",Integer.class,email);
        if(count!=null && count==0) db.update("INSERT INTO account(id,email,display_name,password_hash,role) VALUES(?,?,?,?,?)",UUID.randomUUID(),email,name,encoder.encode(password),role);
    }
}
