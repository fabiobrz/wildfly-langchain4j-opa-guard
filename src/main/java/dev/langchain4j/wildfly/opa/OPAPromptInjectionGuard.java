package dev.langchain4j.wildfly.opa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.styra.opa.wasm.OpaBuiltin;
import com.styra.opa.wasm.OpaPolicy;
import com.styra.opa.wasm.OpaPolicyPool;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Input guardrail whose decision is entirely made by an OPA policy compiled to WebAssembly.
 *
 * <p>The policy calls the {@code llm_score} custom builtin (implemented here with
 * {@link PromptInjectionDetectionService}) only when the deterministic pattern matching is inconclusive.
 */
@ApplicationScoped
public class OPAPromptInjectionGuard implements InputGuardrail {

    public static final String POLICY_PATH_PROPERTY = "opa.policy.path";
    public static final String DEFAULT_POLICY_PATH = "/policies/prompt-injection.wasm";

    /** Upper bound of concurrently evaluated policies; an {@link OpaPolicy} instance is not thread-safe. */
    private static final int POLICY_POOL_SIZE = 4;

    private static final Logger LOGGER = Logger.getLogger(OPAPromptInjectionGuard.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    PromptInjectionDetectionService llmService;

    private OpaPolicyPool policyPool;

    @PostConstruct
    void init() {
        String policyPath = System.getProperty(POLICY_PATH_PROPERTY, DEFAULT_POLICY_PATH);
        byte[] wasm;
        try (InputStream policyStream = getClass().getResourceAsStream(policyPath)) {
            if (policyStream == null) {
                throw new IllegalStateException("Policy file not found: " + policyPath);
            }
            wasm = policyStream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read policy file: " + policyPath, e);
        }

        policyPool = OpaPolicyPool.create(
                () -> OpaPolicy.builder()
                        .withPolicy(wasm)
                        .addBuiltins(OpaBuiltin.from("llm_score", (instance, textNode) -> {
                            String text = textNode.asText();
                            LOGGER.info("OPA guardrail - pattern inconclusive, consulting LLM");
                            double score = llmService.isInjection(text);
                            LOGGER.info("OPA guardrail - LLM score: " + score);
                            return new DoubleNode(score);
                        }))
                        .build(),
                POLICY_POOL_SIZE);
    }

    @PreDestroy
    void destroy() {
        if (policyPool != null) {
            policyPool.close();
        }
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        try {
            ObjectNode input = MAPPER.createObjectNode().put("text", userMessage.singleText());

            String resultJson;
            try (OpaPolicyPool.Loan loan = policyPool.borrow()) {
                resultJson = loan.policy().evaluate(input);
            }

            // OPA result format: [{"result": true|false}]
            JsonNode result = MAPPER.readTree(resultJson);
            boolean allowed = result.path(0).path("result").asBoolean(false);

            if (!allowed) {
                LOGGER.info("OPA guardrail - BLOCKED: prompt injection detected");
                return failure("Prompt injection detected by OPA policy");
            }
            return success();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while evaluating OPA policy", e);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Failed to evaluate OPA policy", e);
        }
    }
}
