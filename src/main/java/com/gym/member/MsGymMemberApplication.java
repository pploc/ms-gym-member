package com.gym.member;

import com.gym.member.config.MemberProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MemberProperties.class)
@ComponentScan(basePackages = {"com.gym.member", "com.gym.common"})
public class MsGymMemberApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsGymMemberApplication.class, args);
    }
}
