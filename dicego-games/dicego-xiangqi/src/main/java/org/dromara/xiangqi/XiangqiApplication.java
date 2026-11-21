package org.dromara.xiangqi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class XiangqiApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiangqiApplication.class, args);
        System.out.println("♟️  中国象棋对战服务启动成功！");
        System.out.println("   前端访问: http://localhost:8080/chess/");
        System.out.println("   WebSocket: ws://localhost:8080/ws/chess");
    }
}
