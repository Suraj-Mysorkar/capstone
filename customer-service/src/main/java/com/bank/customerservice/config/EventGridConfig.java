package com.bank.customerservice.config;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.models.CloudEvent;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import com.azure.messaging.eventgrid.EventGridPublisherClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Wires up the Azure Event Grid publisher client used to publish
 * CustomerRegisteredEvent / CustomerStatusChangedEvent to the shared
 * "Messaging & Events Layer" topic.
 * <p>
 * Prefers Managed Identity (DefaultAzureCredential) in Azure; falls back to
 * a topic access key for local development if configured.
 */
@Configuration
public class EventGridConfig {

    @Value("${azure.eventgrid.topic-endpoint}")
    private String topicEndpoint;

    @Value("${azure.eventgrid.topic-key:}")
    private String topicKey;

    @Bean
    public EventGridPublisherClient<CloudEvent> eventGridPublisherClient() {
        EventGridPublisherClientBuilder clientBuilder =
                new EventGridPublisherClientBuilder().endpoint(topicEndpoint);

        if (StringUtils.hasText(topicKey)) {
            clientBuilder.credential(new AzureKeyCredential(topicKey));
        } else {
            DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().build();
            clientBuilder.credential(credential);
        }

        return clientBuilder.buildCloudEventPublisherClient();
    }
}
