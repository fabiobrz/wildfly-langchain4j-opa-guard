package dev.langchain4j.wildfly.opa;

import dev.langchain4j.cdi.spi.RegisterAIService;
import dev.langchain4j.service.SystemMessage;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAIService(
        chatModelName = "chat-model",
        scope = ApplicationScoped.class,
        inputGuardrails = OPAPromptInjectionGuard.class)
public interface CustomerSupportAgent {

    @SystemMessage("""
            You are a customer support agent of a car rental company 'Miles of Smiles'.
            You are friendly, polite and concise.
            If the question is unrelated to car rental, you should politely redirect the customer to the right department.

            Today is {{current_date}}.
            """)
    String chat(String userMessage);
}
