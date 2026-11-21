package org.dromara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 启动程序
 *
 * @author Lion Li
 */

@SpringBootApplication
public class DromaraApplication {

    /**
     * 应用启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DromaraApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        ConfigurableApplicationContext context = application.run(args);
        String port = context.getEnvironment().getProperty("server.port", "8080");
        String path = context.getEnvironment().getProperty("server.servlet.context-path", "");
        String baseUrl = "http://localhost:" + port + ("/".equals(path) ? "" : path);
        System.out.println("(♥◠‿◠)ﾉﾞ  RuoYi-Vue-Plus启动成功   ლ(´ڡ`ლ)ﾞ");
        System.out.println("后端地址:    " + baseUrl);
        System.out.println("接口文档:    " + baseUrl + "/swagger-ui/index.html");
    }

}
