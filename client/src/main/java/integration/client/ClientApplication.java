package integration.client;

import org.springframework.amqp.core.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.dsl.DirectChannelSpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

@SpringBootApplication
public class ClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }

    private final String destination = "emailJobRequests";

    @Bean
    ApplicationRunner launcher(MessageChannel requests) {
        return args -> requests.send(MessageBuilder.withPayload("hello, world @" + System.currentTimeMillis()).build());
    }

    @Bean
    IntegrationFlow launchJob(AmqpTemplate template) {
        var outboundAdapter = Amqp
                .outboundAdapter(template)
                .routingKey(this.destination);

        return IntegrationFlow
                .from(requests())
                .handle(outboundAdapter)
                .get();
    }

    @Bean
    DirectChannelSpec requests() {
        return MessageChannels.direct();
    }

    @Bean
    Exchange exchange() {
        return ExchangeBuilder.directExchange(this.destination).build();
    }

    @Bean
    Binding binding() {
        return BindingBuilder.bind(this.queue()).to(this.exchange()).with(this.destination).noargs();
    }

    @Bean
    Queue queue() {
        return QueueBuilder.durable(this.destination).build();
    }

}
