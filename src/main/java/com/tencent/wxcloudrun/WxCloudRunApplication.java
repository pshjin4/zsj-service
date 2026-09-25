package com.tencent.wxcloudrun;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(basePackages = {"com.tencent.wxcloudrun.dao"})
public class WxCloudRunApplication {  
  public static void main(String[] args) {
    SpringApplication.run(WxCloudRunApplication.class, args);
    System.out.println("Hello World! This is a Spring Boot application running on Tencent Cloud Run.");
  }
}
