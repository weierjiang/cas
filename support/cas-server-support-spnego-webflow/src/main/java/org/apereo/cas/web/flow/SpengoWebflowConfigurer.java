package org.apereo.cas.web.flow;

import lombok.extern.slf4j.Slf4j;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.web.flow.configurer.AbstractCasWebflowConfigurer;
import org.springframework.context.ApplicationContext;
import org.springframework.webflow.definition.registry.FlowDefinitionRegistry;
import org.springframework.webflow.engine.ActionState;
import org.springframework.webflow.engine.Flow;
import org.springframework.webflow.engine.builder.support.FlowBuilderServices;

/**
 * The {@link SpengoWebflowConfigurer} is responsible for
 * adjusting the CAS webflow context for spnego integration.
 *
 * @author Misagh Moayyed
 * @since 4.2
 */
@Slf4j
public class SpengoWebflowConfigurer extends AbstractCasWebflowConfigurer {

    private static final String SPNEGO = "spnego";
    private static final String START_SPNEGO_AUTHENTICATE = "startSpnegoAuthenticate";
    private static final String SPNEGO_NEGOTIATE = "negociateSpnego";
    private static final String EVALUATE_SPNEGO_CLIENT = "evaluateClientRequest";

    public SpengoWebflowConfigurer(final FlowBuilderServices flowBuilderServices, 
                                   final FlowDefinitionRegistry loginFlowDefinitionRegistry,
                                   final ApplicationContext applicationContext,
                                   final CasConfigurationProperties casProperties) {
        super(flowBuilderServices, loginFlowDefinitionRegistry, applicationContext, casProperties);
    }

    @Override
    protected void doInitialize() {
        final var flow = getLoginFlow();
        if (flow != null) {
            createStartSpnegoAction(flow);
            createEvaluateSpnegoClientAction(flow);

            final var spnego = createSpnegoActionState(flow);
            registerMultifactorProvidersStateTransitionsIntoWebflow(spnego);

            augmentWebflowToStartSpnego(flow);
        }
    }

    private void augmentWebflowToStartSpnego(final Flow flow) {
        final var state = getState(flow, CasWebflowConstants.STATE_ID_INIT_LOGIN_FORM, ActionState.class);
        createTransitionForState(state, CasWebflowConstants.TRANSITION_ID_SUCCESS, START_SPNEGO_AUTHENTICATE, true);
    }

    private void createStartSpnegoAction(final Flow flow) {
        final var actionState = createActionState(flow, START_SPNEGO_AUTHENTICATE, createEvaluateAction(SPNEGO_NEGOTIATE));
        actionState.getTransitionSet().add(createTransition(CasWebflowConstants.TRANSITION_ID_SUCCESS, SPNEGO));
        actionState.getTransitionSet().add(createTransition(CasWebflowConstants.TRANSITION_ID_ERROR, CasWebflowConstants.STATE_ID_VIEW_LOGIN_FORM));
    }

    private ActionState createSpnegoActionState(final Flow flow) {
        final var spnego = createActionState(flow, SPNEGO, createEvaluateAction(SPNEGO));
        final var transitions = spnego.getTransitionSet();
        transitions.add(createTransition(CasWebflowConstants.TRANSITION_ID_SUCCESS, CasWebflowConstants.STATE_ID_CREATE_TICKET_GRANTING_TICKET));
        transitions.add(createTransition(CasWebflowConstants.TRANSITION_ID_ERROR, CasWebflowConstants.STATE_ID_VIEW_LOGIN_FORM));
        transitions.add(createTransition(CasWebflowConstants.TRANSITION_ID_AUTHENTICATION_FAILURE, CasWebflowConstants.STATE_ID_VIEW_LOGIN_FORM));
        spnego.getExitActionList().add(createEvaluateAction(CasWebflowConstants.ACTION_ID_CLEAR_WEBFLOW_CREDENTIALS));
        return spnego;
    }

    private void createEvaluateSpnegoClientAction(final Flow flow) {
        final var evaluateClientRequest = createActionState(flow, EVALUATE_SPNEGO_CLIENT,
                createEvaluateAction(casProperties.getAuthn().getSpnego().getHostNameClientActionStrategy()));
        evaluateClientRequest.getTransitionSet().add(createTransition(CasWebflowConstants.TRANSITION_ID_YES, START_SPNEGO_AUTHENTICATE));
        evaluateClientRequest.getTransitionSet().add(createTransition(CasWebflowConstants.TRANSITION_ID_NO, getStartState(flow)));
    }
}
