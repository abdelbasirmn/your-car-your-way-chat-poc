package com.ycyw.poc.web;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ProblemHandler {
    @ExceptionHandler(ApiProblem.class)
    ResponseEntity<?> business(ApiProblem e){return problem(e.status(),e.code(),e.getMessage());}
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,IllegalArgumentException.class})
    ResponseEntity<?> invalid(Exception e){return problem(400,"INVALID_REQUEST","Requête invalide");}
    private ResponseEntity<?> problem(int status,String code,String title){
        return ResponseEntity.status(status).header("Content-Type","application/problem+json").body(Map.of(
            "type","urn:ycyw:problem:"+code.toLowerCase(),"status",status,"code",code,"title",title,"correlationId",UUID.randomUUID().toString()));
    }
}
