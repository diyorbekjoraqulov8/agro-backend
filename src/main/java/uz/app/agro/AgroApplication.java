package uz.app.agro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AgroApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgroApplication.class, args);
    }
}
