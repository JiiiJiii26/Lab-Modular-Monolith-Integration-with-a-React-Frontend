package edu.cit.pena;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Root application class for the Modular Monolith.
 * Located in 'edu.cit.pena' so default component scanning encompasses
 * both the Order module ('edu.cit.pena.shop') and the Inventory module ('edu.cit.pena.inventory').
 */
@SpringBootApplication
public class ModularMonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModularMonolithApplication.class, args);
    }
}
