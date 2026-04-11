package com.ensemble.worker.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String TASK_QUEUE = "task.queue";
    public static final String RESULTS_QUEUE = "results.queue";
    public static final String EXCHANGE = "ensemble.exchange";

    @Bean
    public Queue taskQueue() { return new Queue(TASK_QUEUE, true); }

    @Bean
    public Queue resultsQueue() { return new Queue(RESULTS_QUEUE, true); }

    @Bean
    public DirectExchange exchange() { return new DirectExchange(EXCHANGE); }

    @Bean
    public Binding taskBinding(Queue taskQueue, DirectExchange exchange) {
        return BindingBuilder.bind(taskQueue).to(exchange).with(TASK_QUEUE);
    }

    @Bean
    public Binding resultsBinding(Queue resultsQueue, DirectExchange exchange) {
        return BindingBuilder.bind(resultsQueue).to(exchange).with(RESULTS_QUEUE);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
