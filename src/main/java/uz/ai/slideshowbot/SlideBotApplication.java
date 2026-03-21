package uz.ai.slideshowbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SlideBotApplication {

	public static void main(String[] args) {
		SpringApplication.run(SlideBotApplication.class, args);
		System.out.println("✅ SlideBot ishga tushdi!");
	}


}
