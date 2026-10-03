package io.github.exepex.commerce.servicenow.exception;

import io.github.exepex.commerce.servicenow.constants.RefusalMessages;

/** The server runs without a ServiceNow instance, so there is no incident to work. */
public class ServiceNowNotConfiguredException extends ToolRefusedException {

    public ServiceNowNotConfiguredException() {
        super(RefusalMessages.SERVICENOW_NOT_CONFIGURED);
    }
}
