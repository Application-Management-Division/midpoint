/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.authentication.impl.security.channel;

import java.util.ArrayList;
import java.util.Collection;

import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.security.api.Authorization;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AuthenticationSequenceChannelType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AuthorizationType;

/**
 * @author skublik
 */

public class SelfRegistrationAuthenticationChannel extends AuthenticationChannelImpl {

    private static final Trace LOGGER = TraceManager.getTrace(SelfRegistrationAuthenticationChannel.class);

    private static final String DOT_CLASS = SelfRegistrationAuthenticationChannel.class.getName() + ".";

    private static final String OPERATION_LOAD_USER = DOT_CLASS + "loadUser";
    private static final String OPERATION_FINISH_REGISTRATION = DOT_CLASS + "finishRegistration";

//    private TaskManager taskManager;
//    private SecurityContextManager securityContextManager;
//    private ModelService modelService;

    public SelfRegistrationAuthenticationChannel(AuthenticationSequenceChannelType channel) {
        super(channel);
    }

    public String getChannelId() {
        return SchemaConstants.CHANNEL_SELF_REGISTRATION_URI;
    }

    public String getPathAfterSuccessfulAuthentication() {
        return "/registration/result";
    }

    public String getPathAfterUnsuccessfulAuthentication() {
        return "/";
    }

    @Override
    public String getSpecificLoginUrl() {
        return "/registration";
    }

//    @Override
//    public void postSuccessAuthenticationProcessing() {
//        OperationResult result = new OperationResult(OPERATION_FINISH_REGISTRATION);
//        try {
//            MidPointPrincipal principal = (MidPointPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
//            UserType user = principal.getUser();
//            PrismObject<UserType> administrator = getAdministratorPrivileged(result);
//
//            assignDefaultRoles(user.getOid(), administrator, result);
//            result.computeStatus();
//            if (result.getStatus() == OperationResultStatus.FATAL_ERROR) {
//                LOGGER.error("Failed to assign default roles, {}", result.getMessage());
//            } else {
//                NonceType nonceClone = user.getCredentials().getNonce().clone();
//                removeNonceAndSetLifecycleState(user.getOid(), nonceClone, administrator, result);
//                assignAdditionalRoleIfPresent(user.getOid(), nonceClone, administrator, result);
//                result.computeStatus();
//            }
//            initLayout(result);
//        } catch (CommonException | AuthenticationException e) {
//            result.computeStatus();
//            initLayout(result);
//        }
//    }
//
//    @NotNull
//    public PrismObject<UserType> getAdministratorPrivileged(OperationResult parentResult) throws CommonException {
//        OperationResult result = parentResult.createSubresult(OPERATION_LOAD_USER);
//        TaskManager manager = taskManager;
//        Task task = manager.createTaskInstance(OPERATION_LOAD_USER);
//
//        task.setChannel(SchemaConstants.CHANNEL_USER_URI);
//        try {
//            return securityContextManager.runPrivilegedChecked(() -> {
//                return modelService
//                        .getObject(UserType.class, SystemObjectsType.USER_ADMINISTRATOR.value(), null, task, result);
//            });
//        } catch (Throwable t) {
//            LOGGER.error("Couldn't get administrator privileged");
//            throw t;
//        } finally {
//            result.computeStatusIfUnknown();
//        }
//    }
//
//    private void assignDefaultRoles(String userOid, PrismObject<UserType> administrator, OperationResult parentResult) throws CommonException {
//        List<ObjectReferenceType> rolesToAssign = getSelfRegistrationConfiguration().getDefaultRoles();
//        if (CollectionUtils.isEmpty(rolesToAssign)) {
//            return;
//        }
//
//        OperationResult result = parentResult.createSubresult(OPERATION_ASSIGN_DEFAULT_ROLES);
//        try {
//            PrismContext prismContext = getPrismContext();
//            List<AssignmentType> assignmentsToCreate = rolesToAssign.stream()
//                    .map(ref -> ObjectTypeUtil.createAssignmentTo(ref, prismContext))
//                    .collect(Collectors.toList());
//            ObjectDelta<Objectable> delta = prismContext.deltaFor(UserType.class)
//                    .item(UserType.F_ASSIGNMENT).addRealValues(assignmentsToCreate)
//                    .asObjectDelta(userOid);
//            runAsChecked(() -> {
//                Task task = createSimpleTask(OPERATION_ASSIGN_DEFAULT_ROLES);
//                WebModelServiceUtils.save(delta, result, task, PageRegistrationConfirmation.this);
//                return null;
//            }, administrator);
//        } catch (CommonException|RuntimeException e) {
//            throw new AuthenticationServiceException("PageRegistrationConfirmation.message.assignDefaultRoles.fatalError", e);
//        } finally {
//            result.computeStatusIfUnknown();
//        }
//    }


    @Override
    public boolean isSupportActivationByChannel() {
        return false;
    }

    @Override
    public Collection<Authorization> resolveAuthorities(Collection<Authorization> authorities) {
        ArrayList<Authorization> newAuthorities = new ArrayList<Authorization>();
        AuthorizationType authorizationType = new AuthorizationType();
        authorizationType.getAction().add(AuthorizationConstants.AUTZ_UI_SELF_REGISTRATION_FINISH_URL);
        Authorization selfServiceCredentialsAuthz = new Authorization(authorizationType);
        newAuthorities.add(selfServiceCredentialsAuthz);
        authorities.addAll(newAuthorities);
        return authorities;
    }
}
