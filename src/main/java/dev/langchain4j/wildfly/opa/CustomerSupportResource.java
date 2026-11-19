package dev.langchain4j.wildfly.opa;

import dev.langchain4j.guardrail.InputGuardrailException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
@Path("/customer-support-agent")
public class CustomerSupportResource {

    private static final Logger LOGGER = Logger.getLogger(CustomerSupportResource.class.getName());

    @Inject
    CustomerSupportAgent customerSupportAgent;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response chat(@QueryParam("message") String message) {
        if (message == null || message.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("The 'message' query parameter is required.")
                    .build();
        }
        try {
            return Response.ok(customerSupportAgent.chat(message)).build();
        } catch (InputGuardrailException e) {
            LOGGER.log(Level.WARNING, "Input guardrails detected a security issue: " + e.getMessage(), e);
            return Response.ok("Sorry, I am unable to process your request at the moment. "
                            + "It's not something I'm allowed to do.")
                    .build();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error calling the LLM", e);
            return Response.ok("I ran into some problems. Please try again.").build();
        }
    }
}
