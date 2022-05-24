/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.login;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.evolveum.midpoint.gui.api.page.PageAdminLTE;

import com.evolveum.midpoint.web.component.message.FeedbackAlerts;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.TransparentWebMarkupContainer;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.WebAttributes;

import com.evolveum.midpoint.authentication.api.config.MidpointAuthentication;
import com.evolveum.midpoint.authentication.api.util.AuthUtil;
import com.evolveum.midpoint.schema.util.SecurityPolicyUtil;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.web.security.MidPointApplication;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AuthenticationSequenceType;

/**
 * @author lskublik
 */
public abstract class AbstractPageLogin extends PageAdminLTE {
    private static final long serialVersionUID = 1L;

    private static final String ID_SEQUENCE = "sequence";
    private static final String ID_SWITCH_TO_DEFAULT_SEQUENCE = "switchToDefaultSequence";

    public AbstractPageLogin(PageParameters parameters) {
        super(parameters);
    }

    public AbstractPageLogin() {
        super(null);
    }

    @Override
    protected void addDefaultBodyStyle(TransparentWebMarkupContainer body) {
        body.add(AttributeModifier.replace("class", "login-page"));
        body.add(AttributeModifier.replace("style", "")); //TODO hack :) because PageBase has min-height defined.
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);

        response.render(OnDomReadyHeaderItem.forScript("$(\"input[name='username']\").focus();"));
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        initLayout();
    }

    private void initLayout() {

        String sequenceName = getSequenceName();
        Label sequence = new Label(ID_SEQUENCE, createStringResource("AbstractPageLogin.authenticationSequence", sequenceName));
        sequence.add(new VisibleBehaviour(() -> !StringUtils.isEmpty(sequenceName)));
        add(sequence);
        AjaxButton toDefault = new AjaxButton(ID_SWITCH_TO_DEFAULT_SEQUENCE, createStringResource("AbstractPageLogin.switchToDefault")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                AuthUtil.clearMidpointAuthentication();
                setResponsePage(getMidpointApplication().getHomePage());
            }
        };
        toDefault.add(new VisibleBehaviour(() -> !StringUtils.isEmpty(sequenceName)));
        add(toDefault);
        initCustomLayout();

        addFeedbackPanel();
    }

    private String getSequenceName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof MidpointAuthentication) {
            MidpointAuthentication mpAuthentication = (MidpointAuthentication) authentication;
            AuthenticationSequenceType sequence = mpAuthentication.getSequence();
            if (sequence != null && sequence.getChannel() != null
                    && !Boolean.TRUE.equals(sequence.getChannel().isDefault())
                    && SecurityPolicyUtil.DEFAULT_CHANNEL.equals(sequence.getChannel().getChannelId())) {
                return sequence.getDisplayName() != null ? sequence.getDisplayName() : sequence.getName();
            }
        }

        return null;
    }

    protected abstract void initCustomLayout();

    @Override
    protected void onConfigure() {
        super.onConfigure();
        showExceptionMessage();
    }

    private void showExceptionMessage() {
        ServletWebRequest req = (ServletWebRequest) RequestCycle.get().getRequest();
        HttpServletRequest httpReq = req.getContainerRequest();
        HttpSession httpSession = httpReq.getSession();

        Exception ex = (Exception) httpSession.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        if (ex == null) {
            return;
        }

        String msg = ex.getMessage();
        if (StringUtils.isEmpty(msg)) {
            msg = "web.security.provider.unavailable";
        }

        String[] msgs = msg.split(";");
        for (String message : msgs) {
            message = getLocalizationService().translate(message, null, getLocale(), message);
            error(message);
        }
        httpSession.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
    }

    @Override
    protected void onBeforeRender() {
        super.onBeforeRender();
        confirmUserPrincipal();
    }

    @Override
    protected void onAfterRender() {
        super.onAfterRender();
    }

    protected void confirmUserPrincipal() {
        if (AuthUtil.getPrincipalUser() != null) {
            MidPointApplication app = getMidpointApplication();
            throw new RestartResponseException(app.getHomePage());
        }
    }

}
